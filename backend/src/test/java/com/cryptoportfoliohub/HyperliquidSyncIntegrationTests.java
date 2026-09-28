package com.cryptoportfoliohub;

import com.cryptoportfoliohub.connection.api.ConnectionCreateRequest;
import com.cryptoportfoliohub.connection.application.ConnectionService;
import com.cryptoportfoliohub.domain.money.PositionSide;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.persistence.entity.Activity;
import com.cryptoportfoliohub.persistence.entity.ActivityLeg;
import com.cryptoportfoliohub.persistence.entity.AssetBalance;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncState;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStateId;
import com.cryptoportfoliohub.persistence.entity.ProviderAccountState;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.entity.SyncRunStatus;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.ActivityLegRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityPerpetualFillDetailRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityRepository;
import com.cryptoportfoliohub.persistence.repository.AssetBalanceRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.persistence.repository.PerpetualPositionRepository;
import com.cryptoportfoliohub.persistence.repository.ProviderAccountStateRepository;
import com.cryptoportfoliohub.persistence.repository.SyncRunRepository;
import com.cryptoportfoliohub.persistence.repository.SyncRunResultRepository;
import com.cryptoportfoliohub.persistence.repository.UserRepository;
import com.cryptoportfoliohub.persistence.entity.PerpetualPosition;
import com.cryptoportfoliohub.provider.ActivityPage;
import com.cryptoportfoliohub.provider.NormalizedActivity;
import com.cryptoportfoliohub.provider.NormalizedActivityLeg;
import com.cryptoportfoliohub.provider.NormalizedActivityType;
import com.cryptoportfoliohub.provider.NormalizedAssetBalance;
import com.cryptoportfoliohub.provider.NormalizedAssetCategory;
import com.cryptoportfoliohub.provider.NormalizedDirection;
import com.cryptoportfoliohub.provider.NormalizedPerpetualFillDetail;
import com.cryptoportfoliohub.provider.NormalizedPerpetualFillDirection;
import com.cryptoportfoliohub.provider.NormalizedPerpetualFillSide;
import com.cryptoportfoliohub.provider.NormalizedPerpetualPosition;
import com.cryptoportfoliohub.provider.NormalizedProviderAccountState;
import com.cryptoportfoliohub.provider.hyperliquid.HyperliquidAccountMode;
import com.cryptoportfoliohub.provider.hyperliquid.HyperliquidAdapter;
import com.cryptoportfoliohub.provider.hyperliquid.HyperliquidCurrentState;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
class HyperliquidSyncIntegrationTests {

    private static final Instant FETCHED_AT = Instant.parse("2026-09-28T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private ConnectionSyncStateRepository syncStateRepository;

    @Autowired
    private AssetBalanceRepository assetBalanceRepository;

    @Autowired
    private ProviderAccountStateRepository accountStateRepository;

    @Autowired
    private PerpetualPositionRepository positionRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityLegRepository activityLegRepository;

    @Autowired
    private ActivityPerpetualFillDetailRepository fillDetailRepository;

    @Autowired
    private SyncRunRepository syncRunRepository;

    @Autowired
    private SyncRunResultRepository syncRunResultRepository;

    @MockitoBean
    private HyperliquidAdapter hyperliquidAdapter;

    @Test
    void manualSyncPersistsBalancesPositionsAccountStatesAndSeparatedActivityDetails() throws Exception {
        User owner = createUser("hyperliquid-sync-owner");
        User other = createUser("hyperliquid-sync-other");
        ConnectionEntity connection = createHyperliquidConnection(owner);
        String address = connection.getExternalAccountRef();
        when(hyperliquidAdapter.fetchCurrentState(address)).thenReturn(currentState("110"));
        when(hyperliquidAdapter.fetchActivities(eq(address), any(Instant.class), any(Instant.class)))
                .thenReturn(new ActivityPage(List.of(spotFill(), perpFill(), funding()), null));

        UUID syncRunId = requestSync(owner, connection.getId());
        String response = awaitRun(owner, connection.getId(), syncRunId, SyncRunStatus.SUCCESS)
                .getResponse().getContentAsString();

        assertThat(json(response, "$.status")).isEqualTo("SUCCESS");
        assertThat(jsonList(response, "$.capabilities[*].status")).containsOnly("SUCCESS");
        assertThat(syncRunResultRepository.findAllOwnedResults(syncRunId, connection.getId(), owner.getId()))
                .hasSize(4);

        List<AssetBalance> balances = assetBalanceRepository
                .findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                        connection.getId(), owner.getId());
        assertThat(balances).singleElement().satisfies(balance -> {
            assertThat(balance.getAssetKey()).isEqualTo("HYPERLIQUID:SPOT:USDC");
            assertThat(balance.getTotalQuantity()).isEqualByComparingTo("100");
            assertThat(balance.getAvailableQuantity()).isEqualByComparingTo("90");
            assertThat(balance.getLockedQuantity()).isEqualByComparingTo("10");
            assertThat(balance.getJpyValue()).isNull();
            assertThat(balance.getLastSuccessSyncRunId()).isEqualTo(syncRunId);
        });

        List<ProviderAccountState> accountStates = accountStateRepository
                .findAllByConnection_User_IdAndConnection_DeletedAtIsNull(owner.getId()).stream()
                .filter(state -> state.getConnection().getId().equals(connection.getId())).toList();
        assertThat(accountStates).hasSize(2);
        assertThat(accountStates).filteredOn(state -> state.getAccountScope().equals("PERP_DEX:DEFAULT"))
                .singleElement().satisfies(state -> {
                    assertThat(state.getAccountMode()).isEqualTo("STANDARD");
                    assertThat(state.getProviderAbstractionMode()).isEqualTo("disabled");
                    assertThat(state.getAccountEquity()).isEqualByComparingTo("110");
                    assertThat(state.getEquityIncludesUnrealizedPnl()).isTrue();
                    assertThat(state.getLastSuccessSyncRunId()).isEqualTo(syncRunId);
                });

        List<PerpetualPosition> positions = positionRepository
                .findAllByConnection_User_IdAndConnection_DeletedAtIsNull(owner.getId()).stream()
                .filter(position -> position.getConnection().getId().equals(connection.getId())).toList();
        assertThat(positions).singleElement().satisfies(position -> {
            assertThat(position.getPositionKey()).isEqualTo("DEFAULT:BTC");
            assertThat(position.getQuantity()).isEqualByComparingTo("0.01");
            assertThat(position.getPriceCurrency()).isEqualTo("USDT");
            assertThat(position.getMarginCurrency()).isEqualTo("USDC");
            assertThat(position.getPnlCurrency()).isEqualTo("USDC");
            assertThat(position.getLastSuccessSyncRunId()).isEqualTo(syncRunId);
        });

        List<Activity> activities = activityRepository
                .findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                        connection.getId(), owner.getId());
        assertThat(activities).hasSize(3);
        Activity storedSpot = byDedupKey(activities, "spot:1");
        Activity storedPerp = byDedupKey(activities, "perp:1");
        Activity storedFunding = byDedupKey(activities, "funding:1");
        List<ActivityLeg> spotLegs = legs(storedSpot, owner);
        assertThat(spotLegs).extracting(ActivityLeg::getDirection).containsExactly(
                com.cryptoportfoliohub.persistence.entity.ActivityDirection.IN,
                com.cryptoportfoliohub.persistence.entity.ActivityDirection.OUT,
                com.cryptoportfoliohub.persistence.entity.ActivityDirection.FEE);
        assertThat(spotLegs.get(0).getOriginalAmount()).isEqualByComparingTo("0.01");
        assertThat(spotLegs.get(0).getOriginalCurrency()).isEqualTo("BTC");

        List<ActivityLeg> perpLegs = legs(storedPerp, owner);
        assertThat(perpLegs).singleElement().satisfies(leg -> {
            assertThat(leg.getDirection()).isEqualTo(
                    com.cryptoportfoliohub.persistence.entity.ActivityDirection.FEE);
            assertThat(leg.getQuantity()).isEqualByComparingTo("0.1");
            assertThat(leg.getOriginalAmount()).isEqualByComparingTo("0.1");
            assertThat(leg.getOriginalCurrency()).isEqualTo("USDC");
        });
        assertThat(legs(storedFunding, owner)).singleElement().satisfies(leg -> {
            assertThat(leg.getDirection()).isEqualTo(
                    com.cryptoportfoliohub.persistence.entity.ActivityDirection.IN);
            assertThat(leg.getQuantity()).isEqualByComparingTo("0.4");
            assertThat(leg.getOriginalCurrency()).isEqualTo("USDC");
        });

        var fillDetail = fillDetailRepository.findByActivity_IdAndActivity_Connection_IdAndActivity_Connection_User_Id(
                storedPerp.getId(), connection.getId(), owner.getId()).orElseThrow();
        assertThat(fillDetail.getInstrumentCode()).isEqualTo("BTC");
        assertThat(fillDetail.getQuantity()).isEqualByComparingTo("0.01");
        assertThat(fillDetail.getPrice()).isEqualByComparingTo("50000");
        assertThat(fillDetail.getStartPosition()).isEqualByComparingTo("0");
        assertThat(fillDetail.getClosedPnl()).isEqualByComparingTo("0");

        for (SyncCapability capability : SyncCapability.values()) {
            assertThat(syncState(owner, connection, capability).getLastSuccessSyncRunId()).isEqualTo(syncRunId);
        }
        assertThat(assetBalanceRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(other.getId()))
                .isEmpty();
        assertThat(accountStateRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(other.getId()))
                .isEmpty();
        assertThat(positionRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(other.getId())).isEmpty();
        assertThat(activityRepository.findByIdAndConnection_User_Id(storedPerp.getId(), other.getId())).isEmpty();
        assertThat(fillDetailRepository.findByActivity_IdAndActivity_Connection_User_Id(
                storedPerp.getId(), other.getId())).isEmpty();
        assertThat(syncRunRepository.findByIdAndConnection_IdAndConnection_User_Id(
                syncRunId, connection.getId(), other.getId())).isEmpty();
        mockMvc.perform(post("/api/v1/connections/{id}/sync", connection.getId())
                        .with(login(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/connections/{connectionId}/sync-runs/{syncRunId}",
                        connection.getId(), syncRunId).with(login(other)))
                .andExpect(status().isNotFound());
        verify(hyperliquidAdapter, times(1)).fetchCurrentState(address);

        var queryStart = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(hyperliquidAdapter).fetchActivities(eq(address), queryStart.capture(), any(Instant.class));
        Instant runStartedAt = syncRunRepository.findById(syncRunId).orElseThrow().getStartedAt();
        assertThat(Duration.between(runStartedAt.minus(Duration.ofDays(90)), queryStart.getValue()).abs())
                .isLessThan(Duration.ofMillis(1));
    }

    @Test
    void repeatedActivityImportDoesNotDuplicateHeadersLegsOrPerpetualDetails() throws Exception {
        User owner = createUser("hyperliquid-dedup-owner");
        ConnectionEntity connection = createHyperliquidConnection(owner);
        String address = connection.getExternalAccountRef();
        when(hyperliquidAdapter.fetchCurrentState(address)).thenReturn(emptyCurrentState());
        when(hyperliquidAdapter.fetchActivities(eq(address), any(Instant.class), any(Instant.class)))
                .thenReturn(new ActivityPage(List.of(spotFill(), perpFill(), funding()), null));

        UUID firstRunId = requestSync(owner, connection.getId());
        awaitRun(owner, connection.getId(), firstRunId, SyncRunStatus.SUCCESS);
        UUID secondRunId = requestSync(owner, connection.getId());
        String secondResponse = awaitRun(owner, connection.getId(), secondRunId, SyncRunStatus.SUCCESS)
                .getResponse().getContentAsString();

        List<Activity> activities = activityRepository
                .findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                        connection.getId(), owner.getId());
        assertThat(activities).hasSize(3);
        int legCount = activities.stream().mapToInt(activity -> legs(activity, owner).size()).sum();
        assertThat(legCount).isEqualTo(5);
        Activity perp = byDedupKey(activities, "perp:1");
        assertThat(fillDetailRepository.findByActivity_IdAndActivity_Connection_IdAndActivity_Connection_User_Id(
                perp.getId(), connection.getId(), owner.getId())).isPresent();
        assertThat(jsonList(secondResponse,
                "$.capabilities[?(@.capability == 'ACTIVITY')].recordsPersisted")).contains(0);
        assertThat(syncRunResultRepository.findAllOwnedResults(secondRunId, connection.getId(), owner.getId()))
                .filteredOn(result -> result.getId().getCapability() == SyncCapability.ACTIVITY)
                .singleElement().satisfies(result -> assertThat(result.getRecordsPersisted()).isZero());
    }

    @Test
    void failedCurrentStateRefreshKeepsPreviousBalancesPositionsAndAccountStates() throws Exception {
        User owner = createUser("hyperliquid-stale-owner");
        ConnectionEntity connection = createHyperliquidConnection(owner);
        String address = connection.getExternalAccountRef();
        when(hyperliquidAdapter.fetchCurrentState(address))
                .thenReturn(currentState("110"))
                .thenThrow(new ProviderException(ProviderErrorCategory.TIMEOUT));
        when(hyperliquidAdapter.fetchActivities(eq(address), any(Instant.class), any(Instant.class)))
                .thenReturn(new ActivityPage(List.of(), null));

        UUID firstRunId = requestSync(owner, connection.getId());
        awaitRun(owner, connection.getId(), firstRunId, SyncRunStatus.SUCCESS);
        Instant balanceLastSuccess = syncState(owner, connection, SyncCapability.BALANCE).getLastSuccessAt();
        Instant positionLastSuccess = syncState(owner, connection, SyncCapability.POSITION).getLastSuccessAt();
        Instant accountLastSuccess = syncState(owner, connection, SyncCapability.ACCOUNT).getLastSuccessAt();

        UUID failedRunId = requestSync(owner, connection.getId());
        String response = awaitRun(owner, connection.getId(), failedRunId, SyncRunStatus.PARTIAL)
                .getResponse().getContentAsString();

        assertThat(jsonList(response, "$.capabilities[?(@.capability == 'BALANCE')].errorCategory"))
                .contains("TIMEOUT");
        assertThat(assetBalanceRepository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                connection.getId(), owner.getId())).singleElement().satisfies(balance -> {
                    assertThat(balance.getTotalQuantity()).isEqualByComparingTo("100");
                    assertThat(balance.getLastSuccessSyncRunId()).isEqualTo(firstRunId);
                });
        assertThat(positionRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(owner.getId()))
                .filteredOn(position -> position.getConnection().getId().equals(connection.getId()))
                .singleElement().satisfies(position -> assertThat(position.getLastSuccessSyncRunId())
                        .isEqualTo(firstRunId));
        assertThat(accountStateRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(owner.getId()))
                .filteredOn(state -> state.getConnection().getId().equals(connection.getId()))
                .allSatisfy(state -> assertThat(state.getLastSuccessSyncRunId()).isEqualTo(firstRunId));
        assertThat(syncState(owner, connection, SyncCapability.BALANCE).getLastSuccessAt())
                .isEqualTo(balanceLastSuccess);
        assertThat(syncState(owner, connection, SyncCapability.POSITION).getLastSuccessAt())
                .isEqualTo(positionLastSuccess);
        assertThat(syncState(owner, connection, SyncCapability.ACCOUNT).getLastSuccessAt())
                .isEqualTo(accountLastSuccess);
        assertThat(syncState(owner, connection, SyncCapability.BALANCE).getStatus().name()).isEqualTo("ERROR");
        verify(hyperliquidAdapter, times(2)).fetchCurrentState(address);
    }

    @Test
    void historyCappedAtProviderLimitIsStoredButNotMarkedComplete() throws Exception {
        User owner = createUser("hyperliquid-history-limit-owner");
        ConnectionEntity connection = createHyperliquidConnection(owner);
        String address = connection.getExternalAccountRef();
        when(hyperliquidAdapter.fetchCurrentState(address)).thenReturn(emptyCurrentState());
        when(hyperliquidAdapter.fetchActivities(eq(address), any(Instant.class), any(Instant.class)))
                .thenReturn(new ActivityPage(List.of(funding()), null, true));

        UUID syncRunId = requestSync(owner, connection.getId());
        String response = awaitRun(owner, connection.getId(), syncRunId, SyncRunStatus.PARTIAL)
                .getResponse().getContentAsString();

        assertThat(jsonList(response, "$.capabilities[?(@.capability == 'ACTIVITY')].status"))
                .contains("FAILED");
        assertThat(activityRepository.findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                connection.getId(), owner.getId())).singleElement();
        ConnectionSyncState activityState = syncState(owner, connection, SyncCapability.ACTIVITY);
        assertThat(activityState.getStatus().name()).isEqualTo("ERROR");
        assertThat(activityState.getLastSuccessAt()).isNull();
        assertThat(activityState.getLastSuccessSyncRunId()).isNull();
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

    private ConnectionEntity createHyperliquidConnection(User owner) {
        String address = "0x" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var response = connectionService.create(owner, new ConnectionCreateRequest(
                ConnectionProvider.HYPERLIQUID, "Test Hyperliquid", null, null, null, address));
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

    private static HyperliquidCurrentState currentState(String accountEquity) {
        return new HyperliquidCurrentState(
                HyperliquidAccountMode.STANDARD,
                "disabled",
                true,
                List.of(new NormalizedAssetBalance(
                        "HYPERLIQUID:SPOT:USDC", "USDC", "USD Coin", NormalizedAssetCategory.CRYPTO,
                        "HYPERLIQUID", "USDC-ID", new BigDecimal("100"), new BigDecimal("90"),
                        new BigDecimal("10"), FETCHED_AT)),
                List.of(
                        new NormalizedProviderAccountState(
                                "ACCOUNT", "STANDARD", "disabled", null,
                                null, null, null, null, null, FETCHED_AT),
                        new NormalizedProviderAccountState(
                                "PERP_DEX:DEFAULT", "STANDARD", "disabled", "USDC",
                                null, null, new BigDecimal(accountEquity), null, true, FETCHED_AT)),
                List.of(new NormalizedPerpetualPosition(
                        "DEFAULT:BTC", "BTC", PositionSide.LONG, new BigDecimal("0.01"),
                        new BigDecimal("49000"), new BigDecimal("50000"), null,
                        "USDT", new BigDecimal("5"), new BigDecimal("20"), "USDC",
                        new BigDecimal("10"), "USDC", FETCHED_AT)));
    }

    private static HyperliquidCurrentState emptyCurrentState() {
        return new HyperliquidCurrentState(
                HyperliquidAccountMode.UNIFIED_ACCOUNT, "unifiedAccount", true,
                List.of(), List.of(new NormalizedProviderAccountState(
                        "ACCOUNT", "UNIFIED_ACCOUNT", "unifiedAccount", null,
                        null, null, null, null, null, FETCHED_AT)), List.of());
    }

    private static NormalizedActivity spotFill() {
        return new NormalizedActivity("spot-hash:1", "spot:1", NormalizedActivityType.BUY,
                "spotFill", "COMPLETED", FETCHED_AT.minusSeconds(60), List.of(
                        leg(0, NormalizedDirection.IN, "HYPERLIQUID:SPOT:BTC", "BTC", "0.01"),
                        leg(1, NormalizedDirection.OUT, "HYPERLIQUID:SPOT:USDC", "USDC", "500"),
                        leg(2, NormalizedDirection.FEE, "HYPERLIQUID:SPOT:USDC", "USDC", "0.1")));
    }

    private static NormalizedActivity perpFill() {
        NormalizedPerpetualFillDetail detail = new NormalizedPerpetualFillDetail(
                "BTC", NormalizedPerpetualFillSide.BUY, NormalizedPerpetualFillDirection.OPEN_LONG,
                "Open Long", new BigDecimal("0.01"), new BigDecimal("50000"), "USDT",
                BigDecimal.ZERO, BigDecimal.ZERO, "USDC");
        return new NormalizedActivity("perp-hash:2", "perp:1", NormalizedActivityType.PERP,
                "perpFill", "COMPLETED", FETCHED_AT.minusSeconds(30),
                List.of(leg(0, NormalizedDirection.FEE, "HYPERLIQUID:SPOT:USDC", "USDC", "0.1")), detail);
    }

    private static NormalizedActivity funding() {
        return new NormalizedActivity("funding-hash", "funding:1", NormalizedActivityType.FUNDING,
                "userFunding", "COMPLETED", FETCHED_AT.minusSeconds(15),
                List.of(leg(0, NormalizedDirection.IN, "HYPERLIQUID:SPOT:USDC", "USDC", "0.4")));
    }

    private static NormalizedActivityLeg leg(
            int index, NormalizedDirection direction, String assetKey, String symbol, String amount) {
        BigDecimal quantity = new BigDecimal(amount);
        return new NormalizedActivityLeg(index, direction, assetKey, symbol, quantity, quantity, symbol);
    }

    private static Activity byDedupKey(List<Activity> activities, String key) {
        return activities.stream().filter(activity -> activity.getDedupKey().equals(key)).findFirst().orElseThrow();
    }

    private List<ActivityLeg> legs(Activity activity, User owner) {
        return activityLegRepository.findAllByActivity_IdAndActivity_Connection_User_IdOrderByLegIndexAsc(
                activity.getId(), owner.getId());
    }

    private static Object json(String body, String path) {
        return com.jayway.jsonpath.JsonPath.read(body, path);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> jsonList(String body, String path) {
        return com.jayway.jsonpath.JsonPath.read(body, path);
    }
}
