package com.cryptoportfoliohub;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.cryptoportfoliohub.connection.api.ConnectionCreateRequest;
import com.cryptoportfoliohub.connection.credential.CredentialEncryptionService;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.ConnectionCredentialRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "app.security.credential-encryption.key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class ConnectionApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private ConnectionCredentialRepository credentialRepository;

    @Autowired
    private CredentialEncryptionService encryptionService;

    @Test
    void connectionEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/connections"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(post("/api/v1/connections").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"SOLANA\",\"walletAddress\":\"11111111111111111111111111111111\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bitbankCredentialsAreEncryptedAndNeverReturned() throws Exception {
        User owner = createUser("bitbank-owner");
        String apiKey = "fixture-bitbank-api-key";
        String apiSecret = "fixture-bitbank-api-secret";

        String response = mockMvc.perform(post("/api/v1/connections")
                        .with(login(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"BITBANK","displayName":"My exchange",
                                 "apiKey":"%s","apiSecret":"%s"}
                                """.formatted(apiKey, apiSecret)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("BITBANK"))
                .andExpect(jsonPath("$.displayName").value("My exchange"))
                .andExpect(jsonPath("$.maskedIdentifier").doesNotExist())
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.capabilities").isArray())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(apiKey, apiSecret, "ciphertext", "API_SECRET");
        var savedConnection = connectionRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
                owner.getId()).getFirst();
        var credentials = credentialRepository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                savedConnection.getId(), owner.getId());
        assertThat(credentials).hasSize(2);
        assertThat(credentials).allSatisfy(credential -> {
            String plaintext = new String(encryptionService.decrypt(credential.encryptedValue()), StandardCharsets.UTF_8);
            assertThat(credential.getCredentialType()).isIn("API_KEY", "API_SECRET");
            assertThat(credential.encryptedValue().toString()).doesNotContain(plaintext);
        });
        assertThat(credentials).anySatisfy(credential -> assertThat(
                new String(encryptionService.decrypt(credential.encryptedValue()), StandardCharsets.UTF_8))
                .isEqualTo(apiKey));
        assertThat(credentials).anySatisfy(credential -> assertThat(
                new String(encryptionService.decrypt(credential.encryptedValue()), StandardCharsets.UTF_8))
                .isEqualTo(apiSecret));
        assertThat(new ConnectionCreateRequest(ConnectionProvider.BITBANK, null, apiKey, apiSecret, null, null)
                .toString()).doesNotContain(apiKey, apiSecret);

        mockMvc.perform(post("/api/v1/connections")
                        .with(login(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"BITBANK","apiKey":"another-key","apiSecret":"another-secret"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void solanaAddressIsValidatedMaskedAndScopedToItsOwner() throws Exception {
        User owner = createUser("solana-owner");
        User other = createUser("solana-other");
        String walletAddress = "11111111111111111111111111111111";

        String otherConnectionBody = mockMvc.perform(post("/api/v1/connections")
                        .with(login(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"BITBANK","apiKey":"other-user-key","apiSecret":"other-user-secret"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID otherConnectionId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(otherConnectionBody, "$.id"));

        var created = mockMvc.perform(post("/api/v1/connections")
                        .with(login(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"SOLANA","walletAddress":"%s","userId":"%s"}
                                """.formatted(walletAddress, other.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("SOLANA"))
                .andExpect(jsonPath("$.displayName").value("Phantom"))
                .andExpect(jsonPath("$.maskedIdentifier").value("11111…1111"))
                .andReturn();
        String connectionId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/connections").with(login(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].provider").value("BITBANK"));
        mockMvc.perform(delete("/api/v1/connections/{id}", connectionId).with(login(other)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/connections/{id}", otherConnectionId).with(login(owner)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM connection_credentials WHERE connection_id = ?",
                Integer.class, otherConnectionId)).isEqualTo(2);

        mockMvc.perform(post("/api/v1/connections")
                        .with(login(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"SOLANA","walletAddress":"%s"}
                                """.formatted(walletAddress)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONNECTION_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/v1/connections")
                        .with(login(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"SOLANA","walletAddress":"1111111111111111111111111111111!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("walletAddress"));
    }

    @Test
    void hyperliquidAddressIsNormalizedMaskedAndDuplicateProtected() throws Exception {
        User owner = createUser("hyperliquid-owner");
        String address = "0x00000000000000000000000000000000000000AB";
        mockMvc.perform(post("/api/v1/connections")
                        .with(login(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"HYPERLIQUID","accountAddress":"%s"}
                                """.formatted(address)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Hyperliquid"))
                .andExpect(jsonPath("$.maskedIdentifier").value("0x0000…00ab"))
                .andExpect(jsonPath("$.capabilities", org.hamcrest.Matchers.hasItems(
                        "BALANCE", "POSITION", "ACTIVITY", "ACCOUNT")));

        mockMvc.perform(post("/api/v1/connections")
                        .with(login(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"HYPERLIQUID","accountAddress":"%s"}
                                """.formatted(address.toLowerCase())))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingConnectionRemovesCredentialsAndCurrentStateButRetainsHistoryAndSnapshot() throws Exception {
        User owner = createUser("delete-owner");
        String body = mockMvc.perform(post("/api/v1/connections")
                        .with(login(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"BITBANK","apiKey":"delete-key","apiSecret":"delete-secret"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID connectionId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
        UUID syncRunId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID activityLegId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        insertConnectionData(owner.getId(), connectionId, syncRunId, activityId, activityLegId, snapshotId);

        mockMvc.perform(delete("/api/v1/connections/{id}", connectionId).with(login(owner)))
                .andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM connection_credentials WHERE connection_id = ?", Integer.class, connectionId))
                .isEqualTo(2);

        mockMvc.perform(delete("/api/v1/connections/{id}", connectionId).with(login(owner)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM connection_credentials WHERE connection_id = ?", Integer.class, connectionId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM connection_sync_states WHERE connection_id = ?", Integer.class, connectionId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset_balances WHERE connection_id = ?", Integer.class, connectionId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM perpetual_positions WHERE connection_id = ?", Integer.class, connectionId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM provider_account_states WHERE connection_id = ?", Integer.class, connectionId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM connections WHERE id = ?", Boolean.class, connectionId))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE id = ?", Integer.class, activityId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_legs WHERE id = ?", Integer.class, activityLegId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sync_runs WHERE id = ?", Integer.class, syncRunId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sync_run_results WHERE sync_run_id = ?", Integer.class, syncRunId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM portfolio_snapshots WHERE id = ?", Integer.class, snapshotId)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/connections").with(login(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(delete("/api/v1/connections/{id}", connectionId).with(login(owner)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private void insertConnectionData(
            UUID userId,
            UUID connectionId,
            UUID syncRunId,
            UUID activityId,
            UUID activityLegId,
            UUID snapshotId) {
        jdbcTemplate.update("""
                INSERT INTO sync_runs (id, connection_id, user_id, trigger_type, status, started_at, finished_at)
                VALUES (?, ?, ?, 'INITIAL', 'SUCCESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, syncRunId, connectionId, userId);
        jdbcTemplate.update("""
                INSERT INTO sync_run_results (sync_run_id, capability, status)
                VALUES (?, 'BALANCE', 'SUCCESS')
                """, syncRunId);
        jdbcTemplate.update("""
                INSERT INTO connection_sync_states (connection_id, user_id, capability, status, last_success_sync_run_id)
                VALUES (?, ?, 'BALANCE', 'READY', ?)
                """, connectionId, userId, syncRunId);
        jdbcTemplate.update("""
                INSERT INTO asset_balances
                    (id, connection_id, user_id, asset_key, symbol, asset_category, total_quantity,
                     valuation_status, fetched_at, last_success_sync_run_id)
                VALUES (?, ?, ?, 'BITBANK:BTC', 'BTC', 'CRYPTO', 1, 'UNAVAILABLE', CURRENT_TIMESTAMP, ?)
                """, UUID.randomUUID(), connectionId, userId, syncRunId);
        jdbcTemplate.update("""
                INSERT INTO perpetual_positions
                    (id, connection_id, user_id, position_key, instrument_code, side, quantity,
                     price_currency, fetched_at, last_success_sync_run_id)
                VALUES (?, ?, ?, 'BTC-PERP', 'BTC', 'LONG', 1, 'USD', CURRENT_TIMESTAMP, ?)
                """, UUID.randomUUID(), connectionId, userId, syncRunId);
        jdbcTemplate.update("""
                INSERT INTO provider_account_states (id, connection_id, user_id, fetched_at, last_success_sync_run_id)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)
                """, UUID.randomUUID(), connectionId, userId, syncRunId);
        jdbcTemplate.update("""
                INSERT INTO activities (id, connection_id, user_id, dedup_key, event_type, occurred_at, imported_at)
                VALUES (?, ?, ?, 'deposit:test', 'DEPOSIT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, activityId, connectionId, userId);
        jdbcTemplate.update("""
                INSERT INTO activity_legs (id, activity_id, leg_index, direction, asset_key, valuation_status)
                VALUES (?, ?, 0, 'IN', 'BITBANK:BTC', 'UNAVAILABLE')
                """, activityLegId, activityId);
        jdbcTemplate.update("""
                INSERT INTO portfolio_snapshots
                    (id, user_id, snapshot_at, data_as_of_at, net_worth_jpy, holdings_value_jpy,
                     directional_value_jpy, stablecoin_value_jpy, market_exposure_jpy, unrealized_pnl_jpy, status)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 100, 100, 100, 0, 100, 0, 'COMPLETE')
                """, snapshotId, userId);
    }

    private User createUser(String prefix) {
        return userRepository.saveAndFlush(new User(
                prefix + "-" + UUID.randomUUID(), prefix + "@example.test", "Test User", null));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor login(User user) {
        return oidcLogin().idToken(token -> token.subject(user.getGoogleSubject())
                .claim("email", user.getEmail()).claim("email_verified", true));
    }
}
