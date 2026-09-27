package com.cryptoportfoliohub;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStateId;
import com.cryptoportfoliohub.persistence.entity.SyncRunResultId;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.repository.ActivityLegRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityRepository;
import com.cryptoportfoliohub.persistence.repository.AssetBalanceRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionCredentialRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.persistence.repository.PerpetualPositionRepository;
import com.cryptoportfoliohub.persistence.repository.PortfolioSnapshotRepository;
import com.cryptoportfoliohub.persistence.repository.ProviderAccountStateRepository;
import com.cryptoportfoliohub.persistence.repository.SyncRunRepository;
import com.cryptoportfoliohub.persistence.repository.SyncRunResultRepository;
import com.cryptoportfoliohub.persistence.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class OwnedRepositoriesIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private ConnectionCredentialRepository credentialRepository;

    @Autowired
    private ConnectionSyncStateRepository syncStateRepository;

    @Autowired
    private SyncRunRepository syncRunRepository;

    @Autowired
    private SyncRunResultRepository syncRunResultRepository;

    @Autowired
    private AssetBalanceRepository assetBalanceRepository;

    @Autowired
    private PerpetualPositionRepository positionRepository;

    @Autowired
    private ProviderAccountStateRepository accountStateRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityLegRepository activityLegRepository;

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Test
    void userScopedRepositoryQueriesNeverReturnAnotherUsersRecords() {
        Fixture fixture = insertFixture();

        assertThat(userRepository.findByGoogleSubject(fixture.googleSubjectA())).isPresent();
        assertThat(connectionRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                fixture.connectionA(), fixture.userA())).isPresent();
        assertThat(connectionRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                fixture.connectionA(), fixture.userB())).isEmpty();
        assertThat(connectionRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(fixture.userA()))
                .extracting("id").containsExactly(fixture.connectionA());

        assertThat(credentialRepository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                fixture.connectionA(), fixture.userA())).hasSize(1);
        assertThat(credentialRepository.findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                fixture.credentialA(), fixture.userB())).isEmpty();

        ConnectionSyncStateId stateIdA = new ConnectionSyncStateId(
                fixture.connectionA(), fixture.userA(), SyncCapability.BALANCE);
        assertThat(syncStateRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(fixture.userA()))
                .hasSize(1);
        assertThat(syncStateRepository.findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                stateIdA, fixture.userB())).isEmpty();

        assertThat(syncRunRepository.findAllByConnection_User_IdOrderByStartedAtDescIdDesc(fixture.userA()))
                .hasSize(1);
        assertThat(syncRunRepository.findByIdAndConnection_User_Id(fixture.syncRunA(), fixture.userB())).isEmpty();
        SyncRunResultId resultIdA = new SyncRunResultId(
                fixture.syncRunA(), SyncCapability.BALANCE);
        assertThat(syncRunResultRepository.findAllBySyncRun_Connection_User_Id(fixture.userA())).hasSize(1);
        assertThat(syncRunResultRepository.findByIdAndSyncRun_Connection_User_Id(resultIdA, fixture.userB()))
                .isEmpty();

        assertThat(assetBalanceRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(fixture.userA()))
                .hasSize(1);
        assertThat(assetBalanceRepository.findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                fixture.balanceA(), fixture.userB())).isEmpty();
        assertThat(positionRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(fixture.userA()))
                .hasSize(1);
        assertThat(positionRepository.findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                fixture.positionA(), fixture.userB())).isEmpty();
        assertThat(accountStateRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(fixture.userA()))
                .hasSize(1);
        assertThat(accountStateRepository.findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                fixture.accountStateA(), fixture.userB())).isEmpty();

        assertThat(activityRepository.findAllByConnection_User_IdOrderByOccurredAtDescIdDesc(fixture.userA()))
                .hasSize(1);
        assertThat(activityRepository.findByIdAndConnection_User_Id(fixture.activityA(), fixture.userB())).isEmpty();
        assertThat(activityLegRepository.findAllByActivity_IdAndActivity_Connection_User_IdOrderByLegIndexAsc(
                fixture.activityA(), fixture.userA())).hasSize(1);
        assertThat(activityLegRepository.findByIdAndActivity_Connection_User_Id(
                fixture.activityLegA(), fixture.userB())).isEmpty();

        assertThat(snapshotRepository.findAllByUser_IdOrderBySnapshotAtDescIdDesc(fixture.userA())).hasSize(1);
        assertThat(snapshotRepository.findByIdAndUser_Id(fixture.snapshotA(), fixture.userB())).isEmpty();
    }

    @Test
    void softDeletedConnectionIsExcludedFromCurrentStateButItsHistoryRemainsOwnedAndReadable() {
        Fixture fixture = insertFixture();
        var connection = connectionRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                fixture.connectionA(), fixture.userA()).orElseThrow();

        connection.softDelete(Instant.now());
        connectionRepository.saveAndFlush(connection);

        assertThat(connectionRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                fixture.connectionA(), fixture.userA())).isEmpty();
        assertThat(assetBalanceRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(fixture.userA()))
                .isEmpty();
        assertThat(positionRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(fixture.userA()))
                .isEmpty();
        assertThat(activityRepository.findByIdAndConnection_User_Id(fixture.activityA(), fixture.userA())).isPresent();
        assertThat(activityLegRepository.findAllByActivity_IdAndActivity_Connection_User_IdOrderByLegIndexAsc(
                fixture.activityA(), fixture.userA())).hasSize(1);
        assertThat(syncRunRepository.findByIdAndConnection_User_Id(fixture.syncRunA(), fixture.userA())).isPresent();
    }

    @Test
    @Transactional
    void credentialDeletionIsScopedToTheAuthenticatedConnectionOwner() {
        Fixture fixture = insertFixture();

        assertThat(credentialRepository.deleteAllByConnection_IdAndConnection_User_Id(
                fixture.connectionA(), fixture.userB())).isZero();
        assertThat(credentialRepository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                fixture.connectionA(), fixture.userA())).hasSize(1);

        assertThat(credentialRepository.deleteAllByConnection_IdAndConnection_User_Id(
                fixture.connectionA(), fixture.userA())).isEqualTo(1L);

        assertThat(credentialRepository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                fixture.connectionA(), fixture.userA())).isEmpty();
        assertThat(credentialRepository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                fixture.connectionB(), fixture.userB())).hasSize(1);
    }

    private Fixture insertFixture() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID connectionA = UUID.randomUUID();
        UUID connectionB = UUID.randomUUID();
        UUID syncRunA = UUID.randomUUID();
        UUID syncRunB = UUID.randomUUID();
        UUID credentialA = UUID.randomUUID();
        UUID balanceA = UUID.randomUUID();
        UUID positionA = UUID.randomUUID();
        UUID accountStateA = UUID.randomUUID();
        UUID activityA = UUID.randomUUID();
        UUID activityLegA = UUID.randomUUID();
        UUID snapshotA = UUID.randomUUID();
        UUID credentialB = UUID.randomUUID();
        UUID balanceB = UUID.randomUUID();
        UUID positionB = UUID.randomUUID();
        UUID accountStateB = UUID.randomUUID();
        UUID activityB = UUID.randomUUID();
        UUID activityLegB = UUID.randomUUID();
        UUID snapshotB = UUID.randomUUID();
        String googleSubjectA = "subject-" + UUID.randomUUID();
        String googleSubjectB = "subject-" + UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO users (id, google_subject, email) VALUES (?, ?, ?)",
                userA, googleSubjectA, "a-" + userA + "@example.test");
        jdbcTemplate.update("INSERT INTO users (id, google_subject, email) VALUES (?, ?, ?)",
                userB, googleSubjectB, "b-" + userB + "@example.test");
        insertConnection(connectionA, userA, "address-a-" + connectionA);
        insertConnection(connectionB, userB, "address-b-" + connectionB);
        insertSyncRun(syncRunA, connectionA, userA);
        insertSyncRun(syncRunB, connectionB, userB);

        jdbcTemplate.update("""
                INSERT INTO connection_credentials
                    (id, connection_id, user_id, credential_type, ciphertext, nonce, key_version)
                VALUES (?, ?, ?, 'TEST', ?, ?, 1)
                """, credentialA, connectionA, userA, new byte[] {1, 2, 3}, new byte[] {4, 5, 6});
        jdbcTemplate.update("""
                INSERT INTO connection_sync_states
                    (connection_id, user_id, capability, status, last_success_sync_run_id)
                VALUES (?, ?, 'BALANCE', 'READY', ?)
                """, connectionA, userA, syncRunA);
        jdbcTemplate.update("""
                INSERT INTO sync_run_results (sync_run_id, capability, status)
                VALUES (?, 'BALANCE', 'SUCCESS')
                """, syncRunA);
        jdbcTemplate.update("""
                INSERT INTO asset_balances
                    (id, connection_id, user_id, asset_key, symbol, asset_category, total_quantity,
                     valuation_status, fetched_at, last_success_sync_run_id)
                VALUES (?, ?, ?, 'SOLANA:SPL:SOL', 'SOL', 'CRYPTO', 2,
                        'UNAVAILABLE', CURRENT_TIMESTAMP, ?)
                """, balanceA, connectionA, userA, syncRunA);
        jdbcTemplate.update("""
                INSERT INTO perpetual_positions
                    (id, connection_id, user_id, position_key, instrument_code, side, quantity,
                     price_currency, fetched_at, last_success_sync_run_id)
                VALUES (?, ?, ?, 'BTC-PERP-LONG', 'BTC-PERP', 'LONG', 1,
                        'USD', CURRENT_TIMESTAMP, ?)
                """, positionA, connectionA, userA, syncRunA);
        jdbcTemplate.update("""
                INSERT INTO provider_account_states
                    (id, connection_id, user_id, fetched_at, last_success_sync_run_id)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)
                """, accountStateA, connectionA, userA, syncRunA);
        jdbcTemplate.update("""
                INSERT INTO activities
                    (id, connection_id, user_id, dedup_key, event_type, occurred_at, imported_at)
                VALUES (?, ?, ?, ?, 'SWAP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, activityA, connectionA, userA, "dedup-" + activityA);
        jdbcTemplate.update("""
                INSERT INTO activity_legs (id, activity_id, leg_index, direction, asset_key, valuation_status)
                VALUES (?, ?, 0, 'OUT', 'SOLANA:SPL:SOL', 'UNAVAILABLE')
                """, activityLegA, activityA);
        jdbcTemplate.update("""
                INSERT INTO portfolio_snapshots
                    (id, user_id, snapshot_at, data_as_of_at, net_worth_jpy, holdings_value_jpy,
                     directional_value_jpy, stablecoin_value_jpy, market_exposure_jpy,
                     unrealized_pnl_jpy, status)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 100, 100, 100, 0, 100, 0, 'COMPLETE')
                """, snapshotA, userA);

        jdbcTemplate.update("""
                INSERT INTO connection_credentials
                    (id, connection_id, user_id, credential_type, ciphertext, nonce, key_version)
                VALUES (?, ?, ?, 'TEST', ?, ?, 1)
                """, credentialB, connectionB, userB, new byte[] {7, 8, 9}, new byte[] {10, 11, 12});
        jdbcTemplate.update("""
                INSERT INTO connection_sync_states
                    (connection_id, user_id, capability, status, last_success_sync_run_id)
                VALUES (?, ?, 'BALANCE', 'READY', ?)
                """, connectionB, userB, syncRunB);
        jdbcTemplate.update("""
                INSERT INTO sync_run_results (sync_run_id, capability, status)
                VALUES (?, 'BALANCE', 'SUCCESS')
                """, syncRunB);
        jdbcTemplate.update("""
                INSERT INTO asset_balances
                    (id, connection_id, user_id, asset_key, symbol, asset_category, total_quantity,
                     valuation_status, fetched_at, last_success_sync_run_id)
                VALUES (?, ?, ?, 'SOLANA:SPL:SOL', 'SOL', 'CRYPTO', 2,
                        'UNAVAILABLE', CURRENT_TIMESTAMP, ?)
                """, balanceB, connectionB, userB, syncRunB);
        jdbcTemplate.update("""
                INSERT INTO perpetual_positions
                    (id, connection_id, user_id, position_key, instrument_code, side, quantity,
                     price_currency, fetched_at, last_success_sync_run_id)
                VALUES (?, ?, ?, 'BTC-PERP-LONG', 'BTC-PERP', 'LONG', 1,
                        'USD', CURRENT_TIMESTAMP, ?)
                """, positionB, connectionB, userB, syncRunB);
        jdbcTemplate.update("""
                INSERT INTO provider_account_states
                    (id, connection_id, user_id, fetched_at, last_success_sync_run_id)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)
                """, accountStateB, connectionB, userB, syncRunB);
        jdbcTemplate.update("""
                INSERT INTO activities
                    (id, connection_id, user_id, dedup_key, event_type, occurred_at, imported_at)
                VALUES (?, ?, ?, ?, 'SWAP', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, activityB, connectionB, userB, "dedup-" + activityB);
        jdbcTemplate.update("""
                INSERT INTO activity_legs (id, activity_id, leg_index, direction, asset_key, valuation_status)
                VALUES (?, ?, 0, 'OUT', 'SOLANA:SPL:SOL', 'UNAVAILABLE')
                """, activityLegB, activityB);
        jdbcTemplate.update("""
                INSERT INTO portfolio_snapshots
                    (id, user_id, snapshot_at, data_as_of_at, net_worth_jpy, holdings_value_jpy,
                     directional_value_jpy, stablecoin_value_jpy, market_exposure_jpy,
                     unrealized_pnl_jpy, status)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 100, 100, 100, 0, 100, 0, 'COMPLETE')
                """, snapshotB, userB);

        return new Fixture(userA, userB, connectionA, connectionB, syncRunA, credentialA, balanceA,
                positionA, accountStateA, activityA, activityLegA, snapshotA, googleSubjectA);
    }

    private void insertConnection(UUID connectionId, UUID userId, String externalAccountRef) {
        jdbcTemplate.update("""
                INSERT INTO connections (id, user_id, provider, external_account_ref, status)
                VALUES (?, ?, 'SOLANA', ?, 'CONNECTED')
                """, connectionId, userId, externalAccountRef);
    }

    private void insertSyncRun(UUID syncRunId, UUID connectionId, UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO sync_runs (id, connection_id, user_id, trigger_type, status, started_at)
                VALUES (?, ?, ?, 'INITIAL', 'SUCCESS', CURRENT_TIMESTAMP)
                """, syncRunId, connectionId, userId);
    }

    private record Fixture(
            UUID userA,
            UUID userB,
            UUID connectionA,
            UUID connectionB,
            UUID syncRunA,
            UUID credentialA,
            UUID balanceA,
            UUID positionA,
            UUID accountStateA,
            UUID activityA,
            UUID activityLegA,
            UUID snapshotA,
            String googleSubjectA) {
    }
}
