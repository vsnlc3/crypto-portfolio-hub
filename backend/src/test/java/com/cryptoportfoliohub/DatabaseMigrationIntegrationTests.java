package com.cryptoportfoliohub;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class DatabaseMigrationIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void flywayCreatesTheMvpSchemaAndCanValidateItAgain() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class);

        assertThat(tableCount).isEqualTo(12);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThatCode(flyway::validate).doesNotThrowAnyException();
        assertThatCode(flyway::migrate).doesNotThrowAnyException();
    }

    @Test
    void databaseConstraintsRejectCrossUserAndOrphanRows() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID syncRunId = UUID.randomUUID();

        insertUser(ownerId);
        insertUser(otherUserId);
        insertConnection(connectionId, ownerId, "BITBANK", null);
        insertSyncRun(syncRunId, connectionId, ownerId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO asset_balances (
                    id, connection_id, user_id, asset_key, symbol, asset_category,
                    total_quantity, valuation_status, fetched_at, last_success_sync_run_id
                ) VALUES (?, ?, ?, 'BTC', 'BTC', 'CRYPTO', 1, 'UNAVAILABLE', CURRENT_TIMESTAMP, ?)
                """, UUID.randomUUID(), connectionId, otherUserId, syncRunId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO sync_run_results (sync_run_id, capability, status)
                VALUES (?, 'BALANCE', 'SUCCESS')
                """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO activity_legs (id, activity_id, leg_index, direction, asset_key, valuation_status)
                VALUES (?, ?, 0, 'IN', 'BTC', 'UNAVAILABLE')
                """, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertConnection(UUID.randomUUID(), ownerId, "UNKNOWN", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeConnectionPartialUniqueIndexAllowsReconnectionAfterSoftDelete() {
        UUID ownerId = UUID.randomUUID();
        UUID firstConnectionId = UUID.randomUUID();
        UUID secondConnectionId = UUID.randomUUID();
        String walletAddress = "TestSolanaWalletAddress";

        insertUser(ownerId);
        insertConnection(firstConnectionId, ownerId, "SOLANA", walletAddress);

        assertThatThrownBy(() -> insertConnection(secondConnectionId, ownerId, "SOLANA", walletAddress))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("UPDATE connections SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", firstConnectionId);
        insertConnection(secondConnectionId, ownerId, "SOLANA", walletAddress);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM connections WHERE user_id = ? AND external_account_ref = ?",
                Integer.class,
                ownerId,
                walletAddress)).isEqualTo(2);
    }

    private void insertUser(UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO users (id, google_subject, email)
                VALUES (?, ?, ?)
                """, userId, "google-subject-" + userId, "user-" + userId + "@example.test");
    }

    private void insertConnection(UUID connectionId, UUID userId, String provider, String externalAccountRef) {
        jdbcTemplate.update("""
                INSERT INTO connections (id, user_id, provider, external_account_ref, status)
                VALUES (?, ?, ?, ?, 'CONNECTED')
                """, connectionId, userId, provider, externalAccountRef);
    }

    private void insertSyncRun(UUID syncRunId, UUID connectionId, UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO sync_runs (id, connection_id, user_id, trigger_type, status, started_at)
                VALUES (?, ?, ?, 'INITIAL', 'SUCCESS', CURRENT_TIMESTAMP)
                """, syncRunId, connectionId, userId);
    }
}
