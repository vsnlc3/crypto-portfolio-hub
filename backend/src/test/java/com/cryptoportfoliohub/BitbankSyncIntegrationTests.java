package com.cryptoportfoliohub;

import com.cryptoportfoliohub.connection.api.ConnectionCreateRequest;
import com.cryptoportfoliohub.connection.application.ConnectionService;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.persistence.entity.Activity;
import com.cryptoportfoliohub.persistence.entity.ActivityLeg;
import com.cryptoportfoliohub.persistence.entity.AssetBalance;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.ConnectionStatus;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncState;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStateId;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.entity.SyncRunStatus;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.entity.ValuationStatus;
import com.cryptoportfoliohub.persistence.repository.ActivityLegRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityRepository;
import com.cryptoportfoliohub.persistence.repository.AssetBalanceRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionCredentialRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.persistence.repository.SyncRunRepository;
import com.cryptoportfoliohub.persistence.repository.SyncRunResultRepository;
import com.cryptoportfoliohub.persistence.repository.UserRepository;
import com.cryptoportfoliohub.provider.NormalizedActivity;
import com.cryptoportfoliohub.provider.NormalizedActivityLeg;
import com.cryptoportfoliohub.provider.NormalizedActivityType;
import com.cryptoportfoliohub.provider.NormalizedAssetBalance;
import com.cryptoportfoliohub.provider.NormalizedAssetCategory;
import com.cryptoportfoliohub.provider.NormalizedDirection;
import com.cryptoportfoliohub.provider.bitbank.BitbankAdapter;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "app.security.credential-encryption.key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class BitbankSyncIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private ConnectionCredentialRepository credentialRepository;

    @Autowired
    private ConnectionSyncStateRepository syncStateRepository;

    @Autowired
    private AssetBalanceRepository assetBalanceRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityLegRepository activityLegRepository;

    @Autowired
    private SyncRunRepository syncRunRepository;

    @Autowired
    private SyncRunResultRepository syncRunResultRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private BitbankAdapter bitbankAdapter;

    @Test
    void manualSyncPersistsBalancesAndActivityLegsAndRecordsCapabilityState() throws Exception {
        User owner = createUser("bitbank-sync-owner");
        ConnectionEntity connection = createBitbankConnection(owner);
        NormalizedActivity swap = swapActivity("FOUND");
        when(bitbankAdapter.fetchBalances(connection.getId(), owner.getId()))
                .thenReturn(List.of(balance("BTC", "0.5", "0.4", "0.1"), balance("JPY", "12000", "10000", "2000")));
        when(bitbankAdapter.fetchActivities(eq(connection.getId()), eq(owner.getId()),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(swap));

        UUID syncRunId = requestSync(owner, connection.getId());
        String response = awaitRun(owner, connection.getId(), syncRunId, SyncRunStatus.SUCCESS)
                .getResponse().getContentAsString();

        assertThat(response).doesNotContain("api-key", "api-secret", "ciphertext");
        assertThat(json(response, "$.status")).isEqualTo("SUCCESS");
        assertThat(jsonList(response, "$.capabilities[*].status")).containsOnly("SUCCESS");
        assertThat(syncRunRepository.findByIdAndConnection_IdAndConnection_User_Id(
                syncRunId, connection.getId(), owner.getId())).isPresent();
        assertThat(syncRunResultRepository.findAllBySyncRun_Connection_User_Id(owner.getId()))
                .filteredOn(result -> syncRunId.equals(result.getId().getSyncRunId()))
                .hasSize(2);

        List<AssetBalance> balances = assetBalanceRepository
                .findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                        connection.getId(), owner.getId());
        assertThat(balances).hasSize(2);
        assertThat(balances).filteredOn(balance -> balance.getAssetKey().equals("BTC")).singleElement().satisfies(btc -> {
            assertThat(btc.getTotalQuantity()).isEqualByComparingTo("0.5");
            assertThat(btc.getAvailableQuantity()).isEqualByComparingTo("0.4");
            assertThat(btc.getLockedQuantity()).isEqualByComparingTo("0.1");
            assertThat(btc.getJpyValue()).isNull();
            assertThat(btc.getValuationStatus()).isEqualTo(ValuationStatus.UNAVAILABLE);
        });

        Activity activity = activityRepository
                .findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                        connection.getId(), owner.getId()).getFirst();
        assertThat(activity.getEventType().name()).isEqualTo("BUY");
        List<ActivityLeg> legs = activityLegRepository
                .findAllByActivity_IdAndActivity_Connection_User_IdOrderByLegIndexAsc(activity.getId(), owner.getId());
        assertThat(legs).hasSize(3);
        assertThat(legs).extracting(ActivityLeg::getDirection).containsExactly(
                com.cryptoportfoliohub.persistence.entity.ActivityDirection.OUT,
                com.cryptoportfoliohub.persistence.entity.ActivityDirection.IN,
                com.cryptoportfoliohub.persistence.entity.ActivityDirection.FEE);
        assertThat(legs.get(0).getOriginalAmount()).isEqualByComparingTo("25000");
        assertThat(legs.get(0).getOriginalCurrency()).isEqualTo("JPY");
        assertThat(legs.get(1).getOriginalAmount()).isEqualByComparingTo("0.25");
        assertThat(legs.get(1).getOriginalCurrency()).isEqualTo("BTC");
        assertThat(legs).allSatisfy(leg -> {
            assertThat(leg.getJpyValue()).isNull();
            assertThat(leg.getValuationStatus()).isEqualTo(ValuationStatus.UNAVAILABLE);
        });

        ConnectionSyncState balanceState = syncState(owner, connection, SyncCapability.BALANCE);
        ConnectionSyncState activityState = syncState(owner, connection, SyncCapability.ACTIVITY);
        assertThat(balanceState.getLastAttemptAt()).isNotNull();
        assertThat(balanceState.getLastSuccessAt()).isNotNull();
        assertThat(balanceState.getLastSuccessSyncRunId()).isEqualTo(syncRunId);
        assertThat(activityState.getLastSuccessSyncRunId()).isEqualTo(syncRunId);
        ArgumentCaptor<Instant> initialStart = ArgumentCaptor.forClass(Instant.class);
        verify(bitbankAdapter).fetchActivities(eq(connection.getId()), eq(owner.getId()),
                initialStart.capture(), any(Instant.class));
        Instant expectedInitialStart = syncRunRepository.findById(syncRunId).orElseThrow()
                .getStartedAt().minus(Duration.ofDays(90));
        assertThat(Duration.between(expectedInitialStart, initialStart.getValue()).abs())
                .isLessThan(Duration.ofMillis(1));
    }

    @Test
    void repeatedHistoryIsIdempotentAndProviderStatusCanAdvance() throws Exception {
        User owner = createUser("bitbank-dedup-owner");
        ConnectionEntity connection = createBitbankConnection(owner);
        NormalizedActivity found = swapActivity("FOUND");
        NormalizedActivity confirmed = swapActivity("CONFIRMED");
        when(bitbankAdapter.fetchBalances(connection.getId(), owner.getId())).thenReturn(List.of());
        when(bitbankAdapter.fetchActivities(eq(connection.getId()), eq(owner.getId()),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(found), List.of(confirmed));

        UUID firstRunId = requestSync(owner, connection.getId());
        awaitRun(owner, connection.getId(), firstRunId, SyncRunStatus.SUCCESS);
        Instant lastActivitySuccess = syncState(owner, connection, SyncCapability.ACTIVITY).getLastSuccessAt();
        UUID secondRunId = requestSync(owner, connection.getId());
        awaitRun(owner, connection.getId(), secondRunId, SyncRunStatus.SUCCESS);

        List<Activity> activities = activityRepository
                .findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                        connection.getId(), owner.getId());
        assertThat(activities).singleElement().satisfies(activity -> assertThat(activity.getStatus()).isEqualTo("CONFIRMED"));
        assertThat(syncRunResultRepository.findAllOwnedResults(secondRunId, connection.getId(), owner.getId()))
                .filteredOn(result -> result.getId().getCapability() == SyncCapability.ACTIVITY)
                .singleElement().satisfies(result -> assertThat(result.getRecordsPersisted()).isEqualTo(1));
        assertThat(activityLegRepository.findAllByActivity_IdAndActivity_Connection_User_IdOrderByLegIndexAsc(
                activities.getFirst().getId(), owner.getId())).hasSize(3);
        ArgumentCaptor<Instant> overlapStart = ArgumentCaptor.forClass(Instant.class);
        verify(bitbankAdapter, times(2)).fetchActivities(eq(connection.getId()), eq(owner.getId()),
                overlapStart.capture(), any(Instant.class));
        assertThat(Duration.between(lastActivitySuccess.minus(Duration.ofHours(1)), overlapStart.getValue()).abs())
                .isLessThan(Duration.ofMillis(1));
        assertThat(Duration.between(lastActivitySuccess.minus(Duration.ofHours(1)),
                overlapStart.getAllValues().getLast()).abs()).isLessThan(Duration.ofMillis(1));
    }

    @Test
    void capabilityFailureKeepsThePreviousSuccessfulCurrentBalanceAndActivity() throws Exception {
        User owner = createUser("bitbank-stale-owner");
        ConnectionEntity connection = createBitbankConnection(owner);
        when(bitbankAdapter.fetchBalances(connection.getId(), owner.getId()))
                .thenReturn(List.of(balance("BTC", "0.5", "0.4", "0.1")));
        when(bitbankAdapter.fetchActivities(eq(connection.getId()), eq(owner.getId()),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(swapActivity("FOUND")));

        UUID firstRunId = requestSync(owner, connection.getId());
        awaitRun(owner, connection.getId(), firstRunId, SyncRunStatus.SUCCESS);
        Instant balanceLastSuccess = syncState(owner, connection, SyncCapability.BALANCE).getLastSuccessAt();
        doThrow(new ProviderException(ProviderErrorCategory.TIMEOUT))
                .when(bitbankAdapter).fetchBalances(connection.getId(), owner.getId());

        UUID failedRunId = requestSync(owner, connection.getId());
        String response = awaitRun(owner, connection.getId(), failedRunId, SyncRunStatus.PARTIAL)
                .getResponse().getContentAsString();

        assertThat(jsonList(response, "$.capabilities[?(@.capability == 'BALANCE')].errorCategory"))
                .contains("TIMEOUT");
        assertThat(assetBalanceRepository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                connection.getId(), owner.getId())).singleElement().satisfies(balance -> {
                    assertThat(balance.getTotalQuantity()).isEqualByComparingTo("0.5");
                    assertThat(balance.getLastSuccessSyncRunId()).isEqualTo(firstRunId);
                });
        assertThat(syncState(owner, connection, SyncCapability.BALANCE).getLastSuccessAt())
                .isEqualTo(balanceLastSuccess);
        assertThat(syncState(owner, connection, SyncCapability.BALANCE).getStatus().name()).isEqualTo("ERROR");
        assertThat(activityRepository.findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                connection.getId(), owner.getId())).hasSize(1);
    }

    @Test
    void activityHeaderAndLegsRollbackAsOneTransaction() throws Exception {
        User owner = createUser("bitbank-atomic-owner");
        ConnectionEntity connection = createBitbankConnection(owner);
        NormalizedActivity invalid = activityWithDuplicateLegIndexes();
        when(bitbankAdapter.fetchBalances(connection.getId(), owner.getId())).thenReturn(List.of());
        when(bitbankAdapter.fetchActivities(eq(connection.getId()), eq(owner.getId()),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(invalid));

        UUID syncRunId = requestSync(owner, connection.getId());
        String response = awaitRun(owner, connection.getId(), syncRunId, SyncRunStatus.PARTIAL)
                .getResponse().getContentAsString();

        assertThat(jsonList(response, "$.capabilities[?(@.capability == 'ACTIVITY')].status"))
                .contains("FAILED");
        assertThat(activityRepository.findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                connection.getId(), owner.getId())).isEmpty();
        assertThat(syncState(owner, connection, SyncCapability.ACTIVITY).getStatus().name()).isEqualTo("ERROR");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_legs leg JOIN activities activity ON activity.id = leg.activity_id "
                        + "WHERE activity.connection_id = ? AND activity.user_id = ?",
                Integer.class, connection.getId(), owner.getId())).isZero();
    }

    @Test
    void userCannotSyncOrReadAnotherUsersConnectionCredentialBalanceActivityOrSyncRun() throws Exception {
        User owner = createUser("bitbank-owner");
        User other = createUser("bitbank-other");
        ConnectionEntity connection = createBitbankConnection(owner);
        when(bitbankAdapter.fetchBalances(connection.getId(), owner.getId()))
                .thenReturn(List.of(balance("BTC", "0.5", "0.4", "0.1")));
        when(bitbankAdapter.fetchActivities(eq(connection.getId()), eq(owner.getId()),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(swapActivity("FOUND")));

        UUID syncRunId = requestSync(owner, connection.getId());
        awaitRun(owner, connection.getId(), syncRunId, SyncRunStatus.SUCCESS);
        Activity activity = activityRepository
                .findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                        connection.getId(), owner.getId()).getFirst();
        UUID credentialId = credentialRepository
                .findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                        connection.getId(), owner.getId()).getFirst().getId();

        assertThat(credentialRepository.findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                credentialId, other.getId())).isEmpty();
        assertThat(credentialRepository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                connection.getId(), other.getId())).isEmpty();
        assertThat(assetBalanceRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(other.getId())).isEmpty();
        assertThat(activityRepository.findByIdAndConnection_User_Id(activity.getId(), other.getId())).isEmpty();
        assertThat(syncRunRepository.findByIdAndConnection_IdAndConnection_User_Id(
                syncRunId, connection.getId(), other.getId())).isEmpty();

        mockMvc.perform(post("/api/v1/connections/{id}/sync", connection.getId())
                        .with(login(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/connections/{connectionId}/sync-runs/{syncRunId}",
                        connection.getId(), syncRunId).with(login(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        verify(bitbankAdapter, never()).fetchBalances(connection.getId(), other.getId());
    }

    private UUID requestSync(User owner, UUID connectionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/connections/{id}/sync", connectionId)
                        .with(login(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andReturn();
        return UUID.fromString(json(result.getResponse().getContentAsString(), "$.syncRunId").toString());
    }

    private MvcResult awaitRun(User owner, UUID connectionId, UUID syncRunId, SyncRunStatus expected)
            throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            MvcResult result = mockMvc.perform(get("/api/v1/connections/{connectionId}/sync-runs/{syncRunId}",
                            connectionId, syncRunId).with(login(owner)))
                    .andExpect(status().isOk())
                    .andReturn();
            if (expected.name().equals(json(result.getResponse().getContentAsString(), "$.status"))) {
                return result;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Sync Run did not reach expected status " + expected + ".");
    }

    private User createUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(new User(
                prefix + "-" + suffix, prefix + "-" + suffix + "@example.test", "Sync Test", null));
    }

    private ConnectionEntity createBitbankConnection(User owner) {
        var response = connectionService.create(owner, new ConnectionCreateRequest(
                ConnectionProvider.BITBANK, "Test bitbank", "fixture-api-key", "fixture-api-secret", null, null));
        return connectionRepository.findByIdAndUser_IdAndDeletedAtIsNull(response.id(), owner.getId()).orElseThrow();
    }

    private ConnectionSyncState syncState(User owner, ConnectionEntity connection, SyncCapability capability) {
        return syncStateRepository.findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                new ConnectionSyncStateId(connection.getId(), owner.getId(), capability), owner.getId()).orElseThrow();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor login(User user) {
        return oidcLogin().idToken(token -> token.subject(user.getGoogleSubject())
                .claim("email", user.getEmail()).claim("email_verified", true));
    }

    private static NormalizedAssetBalance balance(String asset, String total, String available, String locked) {
        return new NormalizedAssetBalance(asset, asset, null,
                "JPY".equals(asset) ? NormalizedAssetCategory.FIAT : NormalizedAssetCategory.CRYPTO,
                "BITBANK", asset.toLowerCase(), new BigDecimal(total), new BigDecimal(available),
                new BigDecimal(locked), Instant.now());
    }

    private static NormalizedActivity swapActivity(String status) {
        return new NormalizedActivity("trade-100", "trade:trade-100", NormalizedActivityType.BUY,
                "SPOT_TRADE:BUY", status, Instant.now().minusSeconds(300), List.of(
                        new NormalizedActivityLeg(0, NormalizedDirection.OUT, "JPY", "JPY",
                                new BigDecimal("25000"), new BigDecimal("25000"), "JPY"),
                        new NormalizedActivityLeg(1, NormalizedDirection.IN, "BTC", "BTC",
                                new BigDecimal("0.25"), new BigDecimal("0.25"), "BTC"),
                        new NormalizedActivityLeg(2, NormalizedDirection.FEE, "BTC", "BTC",
                                new BigDecimal("0.0001"), new BigDecimal("0.0001"), "BTC")));
    }

    private static NormalizedActivity activityWithDuplicateLegIndexes() {
        return new NormalizedActivity("trade-invalid", "trade:trade-invalid", NormalizedActivityType.BUY,
                "SPOT_TRADE:BUY", null, Instant.now().minusSeconds(300), List.of(
                        new NormalizedActivityLeg(0, NormalizedDirection.OUT, "JPY", "JPY",
                                BigDecimal.ONE, BigDecimal.ONE, "JPY"),
                        new NormalizedActivityLeg(0, NormalizedDirection.IN, "BTC", "BTC",
                                BigDecimal.ONE, BigDecimal.ONE, "BTC")));
    }

    private static Object json(String body, String path) {
        return com.jayway.jsonpath.JsonPath.read(body, path);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> jsonList(String body, String path) {
        return com.jayway.jsonpath.JsonPath.read(body, path);
    }
}
