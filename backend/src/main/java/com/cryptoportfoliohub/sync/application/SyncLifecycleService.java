package com.cryptoportfoliohub.sync.application;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ResourceNotFoundException;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncState;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStatus;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.entity.SyncRun;
import com.cryptoportfoliohub.persistence.entity.SyncRunResult;
import com.cryptoportfoliohub.persistence.entity.SyncRunStatus;
import com.cryptoportfoliohub.persistence.entity.SyncResultStatus;
import com.cryptoportfoliohub.persistence.entity.SyncTriggerType;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.persistence.repository.SyncRunRepository;
import com.cryptoportfoliohub.persistence.repository.SyncRunResultRepository;
import com.cryptoportfoliohub.sync.domain.SyncCapabilityOutcome;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncLifecycleService {

    private static final String SAFE_FAILURE_DETAIL = "Provider data could not be synchronized.";

    private final ConnectionRepository connectionRepository;
    private final ConnectionSyncStateRepository syncStateRepository;
    private final SyncRunRepository syncRunRepository;
    private final SyncRunResultRepository syncRunResultRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public SyncLifecycleService(
            ConnectionRepository connectionRepository,
            ConnectionSyncStateRepository syncStateRepository,
            SyncRunRepository syncRunRepository,
            SyncRunResultRepository syncRunResultRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.connectionRepository = connectionRepository;
        this.syncStateRepository = syncStateRepository;
        this.syncRunRepository = syncRunRepository;
        this.syncRunResultRepository = syncRunResultRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SyncExecutionTicket begin(
            UUID userId,
            UUID connectionId,
            SyncTriggerType triggerType,
            Set<SyncCapability> requestedCapabilities,
            ConnectionProvider expectedProvider) {
        if (requestedCapabilities == null || requestedCapabilities.isEmpty()) {
            throw new IllegalArgumentException("At least one supported capability is required.");
        }
        ConnectionEntity connection = connectionRepository
                .findActiveByIdAndUserIdForUpdate(connectionId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        if (connection.getProvider() != expectedProvider) {
            throw new ResourceNotFoundException();
        }
        List<SyncCapability> capabilities = requestedCapabilities.stream().sorted().toList();
        List<ConnectionSyncState> states = syncStateRepository.findAllByConnectionAndUser(connectionId, userId);
        if (states.stream().anyMatch(state -> state.getStatus() == ConnectionSyncStatus.SYNCING)) {
            throw new SyncAlreadyRunningException();
        }

        Instant startedAt = clock.instant();
        SyncRun run = syncRunRepository.saveAndFlush(new SyncRun(connection, triggerType, startedAt));
        Map<SyncCapability, ConnectionSyncState> statesByCapability = new EnumMap<>(SyncCapability.class);
        states.forEach(state -> statesByCapability.put(state.getId().getCapability(), state));
        List<ConnectionSyncState> updatedStates = new ArrayList<>();
        for (SyncCapability capability : capabilities) {
            ConnectionSyncState state = statesByCapability.get(capability);
            if (state == null) {
                state = new ConnectionSyncState(connection, capability, startedAt);
            } else {
                state.startAttempt(startedAt);
            }
            updatedStates.add(state);
        }
        syncStateRepository.saveAll(updatedStates);
        connection.recordSyncStarted(startedAt);

        return new SyncExecutionTicket(run.getId(), userId, connectionId, triggerType, capabilities, startedAt);
    }

    @Transactional
    public void finish(SyncExecutionTicket ticket, List<SyncCapabilityOutcome> suppliedOutcomes) {
        SyncRun run = syncRunRepository.findByIdAndConnection_IdAndConnection_User_Id(
                        ticket.syncRunId(), ticket.connectionId(), ticket.userId())
                .orElseThrow(ResourceNotFoundException::new);
        Instant finishedAt = clock.instant();
        Map<SyncCapability, SyncCapabilityOutcome> outcomes = normalizeOutcomes(ticket, suppliedOutcomes);
        int succeeded = 0;
        int failed = 0;
        ProviderErrorCategory firstFailure = null;
        List<SyncRunResult> results = new ArrayList<>();
        List<ConnectionSyncState> states = syncStateRepository.findAllByConnectionAndUser(
                ticket.connectionId(), ticket.userId());
        Map<SyncCapability, ConnectionSyncState> statesByCapability = new EnumMap<>(SyncCapability.class);
        states.forEach(state -> statesByCapability.put(state.getId().getCapability(), state));

        for (SyncCapability capability : ticket.capabilities()) {
            SyncCapabilityOutcome outcome = outcomes.get(capability);
            String errorCategory = outcome.errorCategory().map(Enum::name).orElse(null);
            results.add(new SyncRunResult(
                    run,
                    capability,
                    outcome.status(),
                    outcome.recordsFetched().orElse(null),
                    outcome.recordsPersisted().orElse(null),
                    ticket.startedAt(),
                    finishedAt,
                    errorCategory,
                    outcome.status() == SyncResultStatus.FAILED ? SAFE_FAILURE_DETAIL : null,
                    outcome.continuationAvailable()));

            ConnectionSyncState state = statesByCapability.get(capability);
            if (state != null) {
                if (outcome.status() == SyncResultStatus.SUCCESS) {
                    state.recordSuccess(ticket.syncRunId(), finishedAt);
                } else if (outcome.status() == SyncResultStatus.FAILED) {
                    state.recordFailure(errorCategory, finishedAt);
                } else {
                    state.recordSkipped(finishedAt);
                }
            }

            if (outcome.status() == SyncResultStatus.SUCCESS) {
                succeeded++;
            } else if (outcome.status() == SyncResultStatus.FAILED) {
                failed++;
                ProviderErrorCategory outcomeCategory = outcome.errorCategory().orElseThrow();
                firstFailure = firstFailure == null || firstFailure == outcomeCategory
                        ? outcomeCategory : ProviderErrorCategory.UNAVAILABLE;
            }
        }

        SyncRunStatus runStatus = succeeded == 0
                ? SyncRunStatus.FAILED
                : failed == 0 ? SyncRunStatus.SUCCESS : SyncRunStatus.PARTIAL;
        String runErrorCategory = runStatus == SyncRunStatus.FAILED
                ? (firstFailure == null ? ProviderErrorCategory.UNAVAILABLE : firstFailure).name() : null;
        run.finish(runStatus, runErrorCategory,
                runStatus == SyncRunStatus.SUCCESS ? null : SAFE_FAILURE_DETAIL, finishedAt);
        syncRunRepository.save(run);
        syncRunResultRepository.saveAll(results);
        if (!states.isEmpty()) {
            syncStateRepository.saveAll(states);
        }
        boolean hadSuccess = succeeded > 0;
        connectionRepository.findByIdAndUser_Id(ticket.connectionId(), ticket.userId())
                .ifPresent(connection -> connection.recordSyncFinished(hadSuccess, finishedAt));
        if (ticket.capabilities().stream().anyMatch(SyncLifecycleService::isPortfolioCapability)) {
            eventPublisher.publishEvent(new PortfolioRelevantSyncCompleted(ticket.userId()));
        }
    }

    private static boolean isPortfolioCapability(SyncCapability capability) {
        return capability == SyncCapability.BALANCE
                || capability == SyncCapability.POSITION
                || capability == SyncCapability.ACCOUNT;
    }

    private Map<SyncCapability, SyncCapabilityOutcome> normalizeOutcomes(
            SyncExecutionTicket ticket, List<SyncCapabilityOutcome> suppliedOutcomes) {
        Map<SyncCapability, SyncCapabilityOutcome> outcomes = new EnumMap<>(SyncCapability.class);
        if (suppliedOutcomes != null) {
            for (SyncCapabilityOutcome outcome : suppliedOutcomes) {
                if (ticket.capabilities().contains(outcome.capability())) {
                    if (outcomes.putIfAbsent(outcome.capability(), outcome) != null) {
                        outcomes.put(outcome.capability(), SyncCapabilityOutcome.failed(
                                outcome.capability(), ProviderErrorCategory.INVALID_RESPONSE));
                    }
                }
            }
        }
        for (SyncCapability capability : ticket.capabilities()) {
            outcomes.putIfAbsent(capability,
                    SyncCapabilityOutcome.failed(capability, ProviderErrorCategory.UNAVAILABLE));
        }
        return outcomes;
    }
}
