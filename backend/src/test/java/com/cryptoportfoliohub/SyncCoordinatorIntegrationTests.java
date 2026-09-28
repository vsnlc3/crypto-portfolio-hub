package com.cryptoportfoliohub;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.error.ResourceNotFoundException;
import com.cryptoportfoliohub.connection.application.ConnectionService;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.ConnectionStatus;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStatus;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.entity.SyncRunStatus;
import com.cryptoportfoliohub.persistence.entity.SyncResultStatus;
import com.cryptoportfoliohub.persistence.entity.SyncTriggerType;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.persistence.repository.SyncRunRepository;
import com.cryptoportfoliohub.persistence.repository.SyncRunResultRepository;
import com.cryptoportfoliohub.persistence.repository.UserRepository;
import com.cryptoportfoliohub.sync.application.ProviderSyncPort;
import com.cryptoportfoliohub.sync.application.SyncAlreadyRunningException;
import com.cryptoportfoliohub.sync.application.SyncCoordinator;
import com.cryptoportfoliohub.sync.application.SyncExecutionTicket;
import com.cryptoportfoliohub.sync.domain.SyncCapabilityOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class SyncCoordinatorIntegrationTests {

    @Autowired
    private SyncCoordinator syncCoordinator;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private ConnectionSyncStateRepository syncStateRepository;

    @Autowired
    private SyncRunRepository syncRunRepository;

    @Autowired
    private SyncRunResultRepository syncRunResultRepository;

    @Test
    void rejectsDuplicateSyncForTheSameConnectionAndReleasesAfterSuccess() throws Exception {
        User user = createUser("sync-duplicate");
        ConnectionEntity connection = createConnection(user, "duplicate-address");
        CountDownLatch operationStarted = new CountDownLatch(1);
        CountDownLatch continueOperation = new CountDownLatch(1);
        ProviderSyncPort blockedPort = port(ticket -> {
            operationStarted.countDown();
            await(continueOperation);
            return List.of(SyncCapabilityOutcome.success(SyncCapability.BALANCE, 1, 1));
        });

        SyncExecutionTicket ticket = syncCoordinator.requestSync(
                user.getId(), connection.getId(), SyncTriggerType.MANUAL, blockedPort);
        assertThat(operationStarted.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> syncCoordinator.requestSync(
                user.getId(), connection.getId(), SyncTriggerType.MANUAL, blockedPort))
                .isInstanceOf(SyncAlreadyRunningException.class);
        assertThatThrownBy(() -> connectionService.delete(connection.getId(), user))
                .isInstanceOf(SyncAlreadyRunningException.class);
        assertThat(syncStateRepository.findAllByConnectionAndUser(connection.getId(), user.getId()))
                .singleElement()
                .satisfies(state -> assertThat(state.getStatus()).isEqualTo(ConnectionSyncStatus.SYNCING));

        continueOperation.countDown();
        awaitStatus(ticket.syncRunId(), SyncRunStatus.SUCCESS);
        assertThat(syncStateRepository.findAllByConnectionAndUser(connection.getId(), user.getId()))
                .singleElement()
                .satisfies(state -> {
                    assertThat(state.getStatus()).isEqualTo(ConnectionSyncStatus.READY);
                    assertThat(state.getLastSuccessSyncRunId()).isEqualTo(ticket.syncRunId());
                });
    }

    @Test
    void runsDifferentConnectionsInParallel() throws Exception {
        User user = createUser("sync-parallel");
        ConnectionEntity first = createConnection(user, "parallel-address-a");
        ConnectionEntity second = createConnection(user, "parallel-address-b");
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ProviderSyncPort port = port(ticket -> {
            entered.countDown();
            await(release);
            return List.of(SyncCapabilityOutcome.success(SyncCapability.BALANCE, 1, 1));
        });

        SyncExecutionTicket firstRun = syncCoordinator.requestSync(
                user.getId(), first.getId(), SyncTriggerType.MANUAL, port);
        SyncExecutionTicket secondRun = syncCoordinator.requestSync(
                user.getId(), second.getId(), SyncTriggerType.MANUAL, port);

        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        awaitStatus(firstRun.syncRunId(), SyncRunStatus.SUCCESS);
        awaitStatus(secondRun.syncRunId(), SyncRunStatus.SUCCESS);
    }

    @Test
    void recordsProviderFailureAndAllowsRetryWithoutAdvancingLastSuccess() throws Exception {
        User user = createUser("sync-error");
        ConnectionEntity connection = createConnection(user, "error-address");
        ProviderSyncPort failingPort = port(ticket -> {
            throw new ProviderException(ProviderErrorCategory.TIMEOUT);
        });

        SyncExecutionTicket failedRun = syncCoordinator.requestSync(
                user.getId(), connection.getId(), SyncTriggerType.MANUAL, failingPort);
        awaitStatus(failedRun.syncRunId(), SyncRunStatus.FAILED);

        var failedConnection = connectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(failedConnection.getStatus()).isEqualTo(ConnectionStatus.ERROR);
        assertThat(failedConnection.getLastAttemptAt()).isNotNull();
        assertThat(failedConnection.getLastSuccessAt()).isNull();
        assertThat(syncStateRepository.findAllByConnectionAndUser(connection.getId(), user.getId()))
                .singleElement()
                .satisfies(state -> assertThat(state.getStatus()).isEqualTo(ConnectionSyncStatus.ERROR));
        assertThat(syncRunResultRepository.findAllBySyncRun_Connection_User_Id(user.getId()))
                .filteredOn(result -> failedRun.syncRunId().equals(result.getId().getSyncRunId()))
                .singleElement()
                .satisfies(result -> assertThat(result.getStatus()).isEqualTo(SyncResultStatus.FAILED));

        ProviderSyncPort successfulPort = port(ticket ->
                List.of(SyncCapabilityOutcome.success(SyncCapability.BALANCE, 2, 2)));
        SyncExecutionTicket successfulRun = syncCoordinator.requestSync(
                user.getId(), connection.getId(), SyncTriggerType.MANUAL, successfulPort);
        awaitStatus(successfulRun.syncRunId(), SyncRunStatus.SUCCESS);

        var finishedConnection = connectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(finishedConnection.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(finishedConnection.getLastSuccessAt()).isNotNull();
        assertThat(finishedConnection.getLastSuccessAt()).isAfterOrEqualTo(finishedConnection.getLastAttemptAt());
    }

    @Test
    void recordsPartialCapabilityFailureAndReleasesConnectionSyncState() throws Exception {
        User user = createUser("sync-partial");
        ConnectionEntity connection = createConnection(user, "partial-address");
        ProviderSyncPort port = port(EnumSet.of(SyncCapability.BALANCE, SyncCapability.ACTIVITY), ticket -> List.of(
                SyncCapabilityOutcome.success(SyncCapability.BALANCE, 2, 2),
                SyncCapabilityOutcome.failed(SyncCapability.ACTIVITY, ProviderErrorCategory.RATE_LIMIT)));

        SyncExecutionTicket ticket = syncCoordinator.requestSync(
                user.getId(), connection.getId(), SyncTriggerType.MANUAL, port);
        awaitStatus(ticket.syncRunId(), SyncRunStatus.PARTIAL);

        assertThat(connectionRepository.findById(connection.getId()).orElseThrow().getStatus())
                .isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(syncStateRepository.findAllByConnectionAndUser(connection.getId(), user.getId()))
                .extracting(state -> state.getId().getCapability(), state -> state.getStatus())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(SyncCapability.BALANCE, ConnectionSyncStatus.READY),
                        org.assertj.core.groups.Tuple.tuple(SyncCapability.ACTIVITY, ConnectionSyncStatus.ERROR));

        ProviderSyncPort retryPort = port(ticket2 ->
                List.of(SyncCapabilityOutcome.success(SyncCapability.BALANCE, 2, 2)));
        SyncExecutionTicket retry = syncCoordinator.requestSync(
                user.getId(), connection.getId(), SyncTriggerType.MANUAL, retryPort);
        awaitStatus(retry.syncRunId(), SyncRunStatus.SUCCESS);
    }

    @Test
    void cannotStartSyncForAnotherUsersConnection() {
        User owner = createUser("sync-owner");
        User otherUser = createUser("sync-other");
        ConnectionEntity connection = createConnection(owner, "owned-address");
        ProviderSyncPort port = port(ticket -> List.of(SyncCapabilityOutcome.success(SyncCapability.BALANCE, 0, 0)));

        assertThatThrownBy(() -> syncCoordinator.requestSync(
                otherUser.getId(), connection.getId(), SyncTriggerType.MANUAL, port))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(syncRunRepository.findAllByConnection_User_IdOrderByStartedAtDescIdDesc(owner.getId()))
                .isEmpty();
    }

    private ProviderSyncPort port(Function<SyncExecutionTicket, List<SyncCapabilityOutcome>> operation) {
        return port(Set.of(SyncCapability.BALANCE), operation);
    }

    private ProviderSyncPort port(
            Set<SyncCapability> capabilities,
            Function<SyncExecutionTicket, List<SyncCapabilityOutcome>> operation) {
        return new ProviderSyncPort() {
            @Override
            public ConnectionProvider provider() {
                return ConnectionProvider.SOLANA;
            }

            @Override
            public Set<SyncCapability> capabilities() {
                return capabilities;
            }

            @Override
            public List<SyncCapabilityOutcome> synchronize(SyncExecutionTicket ticket) {
                return operation.apply(ticket);
            }
        };
    }

    private User createUser(String prefix) {
        return userRepository.saveAndFlush(new User(
                prefix + "-" + UUID.randomUUID(), prefix + "@example.test", "Sync Test", null));
    }

    private ConnectionEntity createConnection(User user, String walletAddress) {
        return connectionRepository.saveAndFlush(new ConnectionEntity(
                user, ConnectionProvider.SOLANA, "Test Wallet", walletAddress, ConnectionStatus.CONNECTED));
    }

    private void awaitStatus(UUID runId, SyncRunStatus expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            var run = syncRunRepository.findById(runId).orElseThrow();
            if (run.getStatus() == expected) {
                assertThat(run.getFinishedAt()).isNotNull();
                return;
            }
            Thread.sleep(25);
        }
        assertThat(syncRunRepository.findById(runId).orElseThrow().getStatus()).isEqualTo(expected);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test operation timed out.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test operation was interrupted.");
        }
    }
}
