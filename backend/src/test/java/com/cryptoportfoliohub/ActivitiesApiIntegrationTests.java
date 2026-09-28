package com.cryptoportfoliohub;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;
import com.cryptoportfoliohub.activity.api.ActivitiesResponse;
import com.cryptoportfoliohub.persistence.entity.Activity;
import com.cryptoportfoliohub.persistence.entity.ActivityDirection;
import com.cryptoportfoliohub.persistence.entity.ActivityLeg;
import com.cryptoportfoliohub.persistence.entity.ActivityPerpetualFillDetail;
import com.cryptoportfoliohub.persistence.entity.ActivityType;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.ConnectionStatus;
import com.cryptoportfoliohub.persistence.entity.PerpetualFillDirection;
import com.cryptoportfoliohub.persistence.entity.PerpetualFillSide;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.ActivityLegRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityPerpetualFillDetailRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class ActivitiesApiIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-09-28T03:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityLegRepository activityLegRepository;

    @Autowired
    private ActivityPerpetualFillDetailRepository fillDetailRepository;

    @Test
    void returnsOwnerScopedHeadersSwapLegsAndPerpetualFillAndIncludesDeletedConnectionHistory() throws Exception {
        User owner = createUser("activities-owner");
        User other = createUser("activities-other");
        ConnectionEntity active = createConnection(owner, "active", false);
        ConnectionEntity deleted = createConnection(owner, "deleted", true);
        ConnectionEntity otherConnection = createConnection(other, "other", false);
        readyActivitySync(active);

        Activity swap = createActivity(active, "swap-1", "provider-swap", ActivityType.SWAP, NOW);
        addLeg(swap, 0, ActivityDirection.OUT, "SOL", "SOL", "10", "10", "SOL");
        addLeg(swap, 1, ActivityDirection.IN, "USDC", "USDC", "1500", "1500", "USDC");
        addLeg(swap, 2, ActivityDirection.FEE, "SOL", "SOL", "0.01", "0.01", "SOL");

        Activity perp = createActivity(active, "perp-1", "provider-fill", ActivityType.PERP, NOW.minusSeconds(5));
        fillDetailRepository.saveAndFlush(new ActivityPerpetualFillDetail(
                perp, "BTC", PerpetualFillSide.BUY, PerpetualFillDirection.OPEN_LONG, "Open Long",
                new BigDecimal("0.1"), new BigDecimal("64000"), "USD", BigDecimal.ZERO,
                null, null));

        Activity deletedHistory = createActivity(deleted, "deleted-history", "provider-old", ActivityType.DEPOSIT,
                NOW.minusSeconds(10));
        addLeg(deletedHistory, 0, ActivityDirection.IN, "ETH", "ETH", "1", "1", "ETH");
        Activity otherActivity = createActivity(otherConnection, "other-event", "private-event", ActivityType.BUY, NOW);
        addLeg(otherActivity, 0, ActivityDirection.IN, "BTC", "BTC", "99", "99", "BTC");

        ActivitiesResponse response = getActivities(owner, null, 20);

        assertThat(response.summary().connectionCount()).isEqualTo(1);
        assertThat(response.summary().syncedConnectionCount()).isEqualTo(1);
        assertThat(response.activities()).extracting(ActivitiesResponse.ActivityItem::providerEventId)
                .containsExactly("provider-swap", "provider-fill", "provider-old");
        ActivitiesResponse.ActivityItem swapItem = response.activities().getFirst();
        assertThat(swapItem.eventType()).isEqualTo(ActivityType.SWAP);
        assertThat(swapItem.originalEventType()).isEqualTo("swap");
        assertThat(swapItem.status()).isEqualTo("CONFIRMED");
        assertThat(swapItem.legs()).extracting(ActivitiesResponse.ActivityLegItem::direction)
                .containsExactly(ActivityDirection.OUT, ActivityDirection.IN, ActivityDirection.FEE);
        assertThat(swapItem.legs().getFirst().quantity()).isEqualByComparingTo("10");
        assertThat(swapItem.legs().getFirst().originalAmount()).isEqualByComparingTo("10");
        assertThat(swapItem.legs().getFirst().jpyValue()).isNull();
        assertThat(swapItem.legs().getFirst().valuationStatus().name()).isEqualTo("UNAVAILABLE");
        assertThat(swapItem.dataStatus().name()).isEqualTo("COMPLETE");
        String swapJson = mockMvc.perform(get("/api/v1/activities").with(login(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(swapJson).at("/activities/0/legs/0/jpyValue").isNull()).isTrue();

        ActivitiesResponse.ActivityItem perpItem = response.activities().get(1);
        assertThat(perpItem.legs()).isEmpty();
        assertThat(perpItem.perpetualFill()).isNotNull();
        assertThat(perpItem.perpetualFill().instrumentCode()).isEqualTo("BTC");
        assertThat(perpItem.perpetualFill().direction()).isEqualTo(PerpetualFillDirection.OPEN_LONG);
        assertThat(perpItem.perpetualFill().quantity()).isEqualByComparingTo("0.1");
        assertThat(perpItem.perpetualFill().priceCurrency()).isEqualTo("USD");

        ActivitiesResponse.ActivityItem deletedItem = response.activities().getLast();
        assertThat(deletedItem.connectionId()).isEqualTo(deleted.getId());
        assertThat(deletedItem.dataStatus().name()).isEqualTo("STALE");

        ActivitiesResponse otherResponse = getActivities(other, null, 20);
        assertThat(otherResponse.activities()).hasSize(1);
        assertThat(otherResponse.activities().getFirst().providerEventId()).isEqualTo("private-event");
        assertThat(otherResponse.activities()).noneMatch(item -> item.id().equals(swap.getId())
                || item.id().equals(deletedHistory.getId()));
    }

    @Test
    void paginatesWithStableOccurredAtAndIdDescendingCursorAndRejectsInvalidInput() throws Exception {
        User owner = createUser("activities-pagination");
        ConnectionEntity connection = createConnection(owner, "pagination", false);
        Instant tieTime = NOW;
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        insertActivity(first, connection, "page-1", tieTime);
        insertActivity(second, connection, "page-2", tieTime);
        insertActivity(third, connection, "page-3", tieTime.minusSeconds(1));

        ActivitiesResponse page1 = getActivities(owner, null, 2);
        assertThat(page1.activities()).extracting(ActivitiesResponse.ActivityItem::id)
                .containsExactly(second, first);
        assertThat(page1.hasMore()).isTrue();
        assertThat(page1.nextCursor()).isNotBlank();

        ActivitiesResponse page2 = getActivities(owner, page1.nextCursor(), 2);
        assertThat(page2.activities()).extracting(ActivitiesResponse.ActivityItem::id).containsExactly(third);
        assertThat(page2.hasMore()).isFalse();
        assertThat(page2.nextCursor()).isNull();

        mockMvc.perform(get("/api/v1/activities").param("limit", "101").with(login(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/activities").param("cursor", "invalid-cursor").with(login(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultAndMaximumPageSizesAreApplied() throws Exception {
        User owner = createUser("activities-limits");
        ConnectionEntity connection = createConnection(owner, "limits", false);
        for (int i = 0; i < 22; i++) {
            insertActivity(UUID.randomUUID(), connection, "limit-" + i, NOW.minusSeconds(i));
        }

        String defaultJson = mockMvc.perform(get("/api/v1/activities").with(login(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ActivitiesResponse defaultPage = objectMapper.readValue(defaultJson, ActivitiesResponse.class);
        assertThat(defaultPage.activities()).hasSize(20);
        assertThat(defaultPage.hasMore()).isTrue();

        ActivitiesResponse maxPage = getActivities(owner, null, 100);
        assertThat(maxPage.activities()).hasSize(22);
        assertThat(maxPage.hasMore()).isFalse();
    }

    @Test
    void reportsPartialAndStaleActivitySyncStateWithoutHidingStoredEvents() throws Exception {
        User owner = createUser("activities-partial");
        ConnectionEntity ready = createConnection(owner, "ready", false);
        ConnectionEntity unsynced = createConnection(owner, "unsynced", false);
        readyActivitySync(ready);
        insertErrorActivitySyncWithoutSuccess(unsynced);
        createActivity(ready, "ready-event", "ready-event", ActivityType.DEPOSIT, NOW);
        Activity previousHistory = createActivity(unsynced, "old-event", "old-event", ActivityType.SWAP,
                NOW.minusSeconds(1));

        ActivitiesResponse partial = getActivities(owner, null, 20);
        assertThat(partial.summary().status().name()).isEqualTo("PARTIAL");
        assertThat(partial.summary().connectionCount()).isEqualTo(2);
        assertThat(partial.summary().syncedConnectionCount()).isEqualTo(1);
        assertThat(partial.activities()).filteredOn(item -> item.id().equals(previousHistory.getId()))
                .singleElement().satisfies(item -> assertThat(item.dataStatus().name()).isEqualTo("UNAVAILABLE"));

        markActivitySyncFailedAfterSuccess(unsynced);
        ActivitiesResponse stale = getActivities(owner, null, 20);
        assertThat(stale.summary().status().name()).isEqualTo("STALE");
        assertThat(stale.summary().syncedConnectionCount()).isEqualTo(2);
        assertThat(stale.activities()).filteredOn(item -> item.id().equals(previousHistory.getId()))
                .singleElement().satisfies(item -> assertThat(item.dataStatus().name()).isEqualTo("STALE"));
    }

    private ActivitiesResponse getActivities(User user, String cursor, int limit) throws Exception {
        var request = get("/api/v1/activities").param("limit", String.valueOf(limit)).with(login(user));
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        String json = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, ActivitiesResponse.class);
    }

    private User createUser(String prefix) {
        return userRepository.saveAndFlush(new User(
                prefix + "-" + UUID.randomUUID(), prefix + "@example.test", "Activity Test User", null));
    }

    private ConnectionEntity createConnection(User user, String suffix, boolean deleted) {
        ConnectionEntity connection = connectionRepository.saveAndFlush(new ConnectionEntity(
                user, ConnectionProvider.HYPERLIQUID, "Hyperliquid " + suffix, "wallet-" + suffix,
                deleted ? ConnectionStatus.DISCONNECTED : ConnectionStatus.CONNECTED));
        if (deleted) {
            connection.softDelete(NOW.minusSeconds(60));
            connectionRepository.saveAndFlush(connection);
        }
        return connection;
    }

    private Activity createActivity(
            ConnectionEntity connection,
            String dedupKey,
            String providerEventId,
            ActivityType type,
            Instant occurredAt) {
        return activityRepository.saveAndFlush(new Activity(
                connection, dedupKey, providerEventId, type, type.name().toLowerCase(), "CONFIRMED",
                occurredAt, NOW));
    }

    private void addLeg(
            Activity activity,
            int index,
            ActivityDirection direction,
            String assetKey,
            String symbol,
            String quantity,
            String originalAmount,
            String originalCurrency) {
        activityLegRepository.saveAndFlush(new ActivityLeg(
                activity, index, direction, assetKey, symbol, new BigDecimal(quantity),
                new BigDecimal(originalAmount), originalCurrency));
    }

    private void readyActivitySync(ConnectionEntity connection) {
        UUID syncRunId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO sync_runs (id, connection_id, user_id, trigger_type, status, started_at, finished_at)
                VALUES (?, ?, ?, 'INITIAL', 'SUCCESS', ?, ?)
                """, syncRunId, connection.getId(), connection.getUserId(), Timestamp.from(NOW), Timestamp.from(NOW));
        jdbcTemplate.update("""
                INSERT INTO connection_sync_states (
                    connection_id, user_id, capability, status, last_attempt_at, last_success_at,
                    last_success_sync_run_id, updated_at)
                VALUES (?, ?, 'ACTIVITY', 'READY', ?, ?, ?, ?)
                """, connection.getId(), connection.getUserId(), Timestamp.from(NOW), Timestamp.from(NOW),
                syncRunId, Timestamp.from(NOW));
    }

    private void insertErrorActivitySyncWithoutSuccess(ConnectionEntity connection) {
        jdbcTemplate.update("""
                INSERT INTO connection_sync_states (
                    connection_id, user_id, capability, status, last_attempt_at,
                    last_error_category, updated_at)
                VALUES (?, ?, 'ACTIVITY', 'ERROR', ?, 'RATE_LIMIT', ?)
                """, connection.getId(), connection.getUserId(), Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private void markActivitySyncFailedAfterSuccess(ConnectionEntity connection) {
        UUID syncRunId = UUID.randomUUID();
        Instant successfulAt = NOW.minusSeconds(30);
        jdbcTemplate.update("""
                INSERT INTO sync_runs (id, connection_id, user_id, trigger_type, status, started_at, finished_at)
                VALUES (?, ?, ?, 'INITIAL', 'SUCCESS', ?, ?)
                """, syncRunId, connection.getId(), connection.getUserId(),
                Timestamp.from(successfulAt), Timestamp.from(successfulAt));
        jdbcTemplate.update("""
                UPDATE connection_sync_states
                SET status = 'ERROR', last_attempt_at = ?, last_success_at = ?,
                    last_success_sync_run_id = ?, last_error_category = 'TIMEOUT', updated_at = ?
                WHERE connection_id = ? AND user_id = ? AND capability = 'ACTIVITY'
                """, Timestamp.from(NOW), Timestamp.from(successfulAt), syncRunId, Timestamp.from(NOW),
                connection.getId(), connection.getUserId());
    }

    private void insertActivity(UUID id, ConnectionEntity connection, String dedupKey, Instant occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO activities (
                    id, connection_id, user_id, dedup_key, provider_event_id, event_type,
                    original_event_type, status, occurred_at, imported_at, created_at)
                VALUES (?, ?, ?, ?, ?, 'OTHER', 'test-event', 'CONFIRMED', ?, ?, ?)
                """, id, connection.getId(), connection.getUserId(), dedupKey, dedupKey,
                Timestamp.from(occurredAt), Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private RequestPostProcessor login(User user) {
        return oidcLogin().idToken(token -> token.subject(user.getGoogleSubject())
                .claim("email", user.getEmail()).claim("email_verified", true));
    }
}
