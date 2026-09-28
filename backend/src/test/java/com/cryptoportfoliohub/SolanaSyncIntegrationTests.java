package com.cryptoportfoliohub;

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
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.persistence.repository.UserRepository;
import com.cryptoportfoliohub.provider.ActivityPage;
import com.cryptoportfoliohub.provider.ActivityProvider;
import com.cryptoportfoliohub.provider.BalanceProvider;
import com.cryptoportfoliohub.provider.NormalizedActivity;
import com.cryptoportfoliohub.provider.NormalizedActivityLeg;
import com.cryptoportfoliohub.provider.NormalizedActivityType;
import com.cryptoportfoliohub.provider.NormalizedAssetBalance;
import com.cryptoportfoliohub.provider.NormalizedAssetCategory;
import com.cryptoportfoliohub.provider.NormalizedDirection;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
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
class SolanaSyncIntegrationTests {

    private static final String WALLET_ADDRESS = "11111111111111111111111111111111";
    private static final Instant ACTIVITY_AT = Instant.parse("2026-09-20T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private ConnectionSyncStateRepository syncStateRepository;

    @Autowired
    private AssetBalanceRepository assetBalanceRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityLegRepository activityLegRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private BalanceProvider balanceProvider;

    @MockitoBean
    private ActivityProvider activityProvider;

    @Test
    void manualSyncPersistsBalanceAndActivityAtomicallyAndReturnsSafeContinuationMetadata() throws Exception {
        User owner = createUser("sync-owner");
        ConnectionEntity connection = createSolanaConnection(owner);
        NormalizedActivity swap = swapActivity();
        when(balanceProvider.fetchBalances(WALLET_ADDRESS)).thenReturn(List.of(balance("SOL", "1.25")));
        when(activityProvider.fetchActivities(eq(WALLET_ADDRESS), isNull(), eq(100), any(Instant.class)))
                .thenReturn(new ActivityPage(List.of(swap), "page-cursor-2"));

        UUID syncRunId = requestSync(owner, connection.getId());
        MvcResult completed = awaitRun(owner, connection.getId(), syncRunId, SyncRunStatus.SUCCESS);
        String response = completed.getResponse().getContentAsString();

        assertThat(response).doesNotContain("page-cursor-2", "provider_cursor");
        assertThat(json(response, "$.triggerType"))
                .isEqualTo("MANUAL");
        assertThat(json(response, "$.capabilities[0].status"))
                .isEqualTo("SUCCESS");
        assertThat(jsonList(response,
                "$.capabilities[?(@.capability == 'ACTIVITY')].continuationAvailable"))
                .contains(Boolean.TRUE);

        List<AssetBalance> balances = assetBalanceRepository
                .findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                        connection.getId(), owner.getId());
        assertThat(balances).singleElement().satisfies(balance -> {
            assertThat(balance.getTotalQuantity()).isEqualByComparingTo("1.25");
            assertThat(balance.getJpyValue()).isNull();
            assertThat(balance.getValuationStatus()).isEqualTo(ValuationStatus.UNAVAILABLE);
        });

        List<Activity> activities = activityRepository.findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                connection.getId(), owner.getId());
        assertThat(activities).singleElement().satisfies(activity -> {
            assertThat(activity.getEventType().name()).isEqualTo("SWAP");
            List<ActivityLeg> legs = activityLegRepository
                    .findAllByActivity_IdAndActivity_Connection_User_IdOrderByLegIndexAsc(
                            activity.getId(), owner.getId());
            assertThat(legs).hasSize(2);
            assertThat(legs).extracting(ActivityLeg::getDirection)
                    .containsExactly(com.cryptoportfoliohub.persistence.entity.ActivityDirection.OUT,
                            com.cryptoportfoliohub.persistence.entity.ActivityDirection.IN);
            assertThat(legs).allSatisfy(leg -> {
                assertThat(leg.getJpyValue()).isNull();
                assertThat(leg.getValuationStatus()).isEqualTo(ValuationStatus.UNAVAILABLE);
            });
            assertThat(legs.getFirst().getOriginalAmount()).isEqualByComparingTo("1");
            assertThat(legs.getFirst().getOriginalCurrency()).isEqualTo("SOL");
            assertThat(legs.get(1).getOriginalAmount()).isEqualByComparingTo("150");
            assertThat(legs.get(1).getOriginalCurrency()).isEqualTo("USDC");
        });

        ConnectionSyncState activityState = syncState(owner, connection, SyncCapability.ACTIVITY);
        assertThat(activityState.getProviderCursor()).isEqualTo("page-cursor-2");
        assertThat(activityState.getCursorWindowStartAt()).isNotNull();
        assertThat(activityState.getLastSuccessAt()).isNotNull();
        ArgumentCaptor<Instant> initialWindowCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(activityProvider).fetchActivities(
                eq(WALLET_ADDRESS), isNull(), eq(100), initialWindowCaptor.capture());
        Instant expected90DayStart = Instant.now().minus(Duration.ofDays(90));
        assertThat(initialWindowCaptor.getValue())
                .isBetween(expected90DayStart.minusSeconds(60), expected90DayStart.plusSeconds(60));
    }

    @Test
    void continuationResumesSavedWindowAndDeduplicatesExistingActivity() throws Exception {
        User owner = createUser("sync-continuation");
        ConnectionEntity connection = createSolanaConnection(owner);
        NormalizedActivity activity = swapActivity();
        when(balanceProvider.fetchBalances(WALLET_ADDRESS))
                .thenReturn(List.of(balance("SOL", "1.25")), List.of(balance("SOL", "1.25")));
        when(activityProvider.fetchActivities(eq(WALLET_ADDRESS), isNull(), eq(100), any(Instant.class)))
                .thenReturn(new ActivityPage(List.of(activity), "page-cursor-2"));
        when(activityProvider.fetchActivities(eq(WALLET_ADDRESS), eq("page-cursor-2"), eq(100), any(Instant.class)))
                .thenReturn(new ActivityPage(List.of(activity), null));

        UUID firstRunId = requestSync(owner, connection.getId());
        awaitRun(owner, connection.getId(), firstRunId, SyncRunStatus.SUCCESS);
        Instant savedWindowStart = syncState(owner, connection, SyncCapability.ACTIVITY).getCursorWindowStartAt();

        UUID secondRunId = requestSync(owner, connection.getId());
        String response = awaitRun(owner, connection.getId(), secondRunId, SyncRunStatus.SUCCESS)
                .getResponse().getContentAsString();

        assertThat(jsonList(response,
                "$.capabilities[?(@.capability == 'ACTIVITY')].recordsFetched"))
                .contains(1);
        assertThat(jsonList(response,
                "$.capabilities[?(@.capability == 'ACTIVITY')].recordsPersisted"))
                .contains(0);
        assertThat(jsonList(response,
                "$.capabilities[?(@.capability == 'ACTIVITY')].continuationAvailable"))
                .contains(Boolean.FALSE);
        assertThat(activityRepository.findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                connection.getId(), owner.getId())).hasSize(1);
        Activity persisted = activityRepository.findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                connection.getId(), owner.getId()).getFirst();
        assertThat(activityLegRepository.findAllByActivity_IdAndActivity_Connection_User_IdOrderByLegIndexAsc(
                persisted.getId(), owner.getId())).hasSize(2);

        ConnectionSyncState activityState = syncState(owner, connection, SyncCapability.ACTIVITY);
        assertThat(activityState.getProviderCursor()).isNull();
        assertThat(activityState.getCursorWindowStartAt()).isNull();
        assertThat(savedWindowStart).isNotNull();
        verify(activityProvider, times(2)).fetchActivities(eq(WALLET_ADDRESS), any(), eq(100), any(Instant.class));
        verify(activityProvider).fetchActivities(eq(WALLET_ADDRESS), eq("page-cursor-2"), eq(100),
                eq(savedWindowStart));
    }

    @Test
    void activityFailureKeepsPreviousHistoryAndCursorWhileBalanceCapabilitySucceeds() throws Exception {
        User owner = createUser("sync-partial");
        ConnectionEntity connection = createSolanaConnection(owner);
        when(balanceProvider.fetchBalances(WALLET_ADDRESS))
                .thenReturn(List.of(balance("SOL", "1.25")), List.of(balance("ETH", "2.5")));
        when(activityProvider.fetchActivities(eq(WALLET_ADDRESS), isNull(), eq(100), any(Instant.class)))
                .thenReturn(new ActivityPage(List.of(swapActivity()), "page-cursor-preserved"));

        UUID firstRunId = requestSync(owner, connection.getId());
        awaitRun(owner, connection.getId(), firstRunId, SyncRunStatus.SUCCESS);
        ConnectionSyncState firstActivityState = syncState(owner, connection, SyncCapability.ACTIVITY);
        Instant previousActivitySuccess = firstActivityState.getLastSuccessAt();
        doThrow(new ProviderException(ProviderErrorCategory.RATE_LIMIT))
                .when(activityProvider)
                .fetchActivities(eq(WALLET_ADDRESS), eq("page-cursor-preserved"), eq(100), any(Instant.class));

        UUID secondRunId = requestSync(owner, connection.getId());
        String response = awaitRun(owner, connection.getId(), secondRunId, SyncRunStatus.PARTIAL)
                .getResponse().getContentAsString();

        assertThat(jsonList(response,
                "$.capabilities[?(@.capability == 'BALANCE')].status"))
                .contains("SUCCESS");
        assertThat(jsonList(response,
                "$.capabilities[?(@.capability == 'ACTIVITY')].errorCategory"))
                .contains("RATE_LIMIT");
        assertThat(response).doesNotContain("raw", "provider_cursor");
        assertThat(assetBalanceRepository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                connection.getId(), owner.getId()))
                .extracting(AssetBalance::getSymbol)
                .containsExactly("ETH");
        assertThat(activityRepository.findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                connection.getId(), owner.getId())).hasSize(1);

        ConnectionSyncState activityState = syncState(owner, connection, SyncCapability.ACTIVITY);
        assertThat(activityState.getStatus().name()).isEqualTo("ERROR");
        assertThat(activityState.getLastSuccessAt()).isEqualTo(previousActivitySuccess);
        assertThat(activityState.getProviderCursor()).isEqualTo("page-cursor-preserved");
        assertThat(syncState(owner, connection, SyncCapability.BALANCE).getLastSuccessSyncRunId())
                .isEqualTo(secondRunId);
    }

    @Test
    void syncEndpointsRequireCsrfAndHideOtherUsersRunsAndConnections() throws Exception {
        User owner = createUser("sync-api-owner");
        User other = createUser("sync-api-other");
        ConnectionEntity connection = createSolanaConnection(owner);
        when(balanceProvider.fetchBalances(WALLET_ADDRESS)).thenReturn(List.of());
        when(activityProvider.fetchActivities(eq(WALLET_ADDRESS), isNull(), eq(100), any(Instant.class)))
                .thenReturn(new ActivityPage(List.of(), null));

        mockMvc.perform(post("/api/v1/connections/{id}/sync", connection.getId()).with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/connections/{id}/sync", connection.getId()).with(login(owner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/connections/{id}/sync", connection.getId())
                        .with(login(other)).with(csrf()))
                .andExpect(status().isNotFound());

        UUID syncRunId = requestSync(owner, connection.getId());
        awaitRun(owner, connection.getId(), syncRunId, SyncRunStatus.SUCCESS);
        mockMvc.perform(get("/api/v1/connections/{connectionId}/sync-runs/{syncRunId}",
                        connection.getId(), syncRunId).with(login(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void balanceFailureKeepsPreviousCompleteSetWhileActivityCanSucceed() throws Exception {
        User owner = createUser("sync-balance-failure");
        ConnectionEntity connection = createSolanaConnection(owner);
        when(balanceProvider.fetchBalances(WALLET_ADDRESS)).thenReturn(List.of(balance("SOL", "1.25")));
        when(activityProvider.fetchActivities(eq(WALLET_ADDRESS), isNull(), eq(100), any(Instant.class)))
                .thenReturn(new ActivityPage(List.of(swapActivity()), null))
                .thenReturn(new ActivityPage(List.of(depositActivity()), null));

        UUID firstRunId = requestSync(owner, connection.getId());
        awaitRun(owner, connection.getId(), firstRunId, SyncRunStatus.SUCCESS);
        Instant previousActivitySuccess = syncState(owner, connection, SyncCapability.ACTIVITY).getLastSuccessAt();
        doThrow(new ProviderException(ProviderErrorCategory.TIMEOUT))
                .when(balanceProvider).fetchBalances(WALLET_ADDRESS);

        UUID secondRunId = requestSync(owner, connection.getId());
        String response = awaitRun(owner, connection.getId(), secondRunId, SyncRunStatus.PARTIAL)
                .getResponse().getContentAsString();

        assertThat(jsonList(response, "$.capabilities[?(@.capability == 'BALANCE')].status"))
                .contains("FAILED");
        assertThat(jsonList(response, "$.capabilities[?(@.capability == 'BALANCE')].errorCategory"))
                .contains("TIMEOUT");
        assertThat(jsonList(response, "$.capabilities[?(@.capability == 'ACTIVITY')].status"))
                .contains("SUCCESS");
        assertThat(assetBalanceRepository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                connection.getId(), owner.getId()))
                .extracting(AssetBalance::getSymbol)
                .containsExactly("SOL");
        assertThat(activityRepository.findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
                connection.getId(), owner.getId())).hasSize(2);
        assertThat(syncState(owner, connection, SyncCapability.BALANCE).getLastSuccessSyncRunId())
                .isEqualTo(firstRunId);
        Instant expectedOverlapStart = Instant.ofEpochSecond(
                previousActivitySuccess.minus(Duration.ofHours(1)).getEpochSecond());
        verify(activityProvider, times(2)).fetchActivities(eq(WALLET_ADDRESS), isNull(), eq(100), any(Instant.class));
        verify(activityProvider).fetchActivities(eq(WALLET_ADDRESS), isNull(), eq(100), eq(expectedOverlapStart));
    }

    @Test
    void activityHeaderAndLegsRollbackTogetherWhenLegPersistenceFails() throws Exception {
        User owner = createUser("sync-activity-atomicity");
        ConnectionEntity connection = createSolanaConnection(owner);
        when(balanceProvider.fetchBalances(WALLET_ADDRESS)).thenReturn(List.of());
        when(activityProvider.fetchActivities(eq(WALLET_ADDRESS), isNull(), eq(100), any(Instant.class)))
                .thenReturn(new ActivityPage(List.of(activityWithDuplicateLegIndexes()), null));

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

    private UUID requestSync(User owner, UUID connectionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/connections/{id}/sync", connectionId)
                        .with(login(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.triggerType").value("MANUAL"))
                .andReturn();
        return UUID.fromString(json(
                result.getResponse().getContentAsString(), "$.syncRunId").toString());
    }

    private MvcResult awaitRun(User owner, UUID connectionId, UUID syncRunId, SyncRunStatus expected)
            throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            MvcResult result = mockMvc.perform(get("/api/v1/connections/{connectionId}/sync-runs/{syncRunId}",
                            connectionId, syncRunId).with(login(owner)))
                    .andExpect(status().isOk())
                    .andReturn();
            String status = json(result.getResponse().getContentAsString(), "$.status").toString();
            if (expected.name().equals(status)) {
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

    private ConnectionEntity createSolanaConnection(User owner) {
        return connectionRepository.saveAndFlush(new ConnectionEntity(
                owner, ConnectionProvider.SOLANA, "Test Wallet", WALLET_ADDRESS, ConnectionStatus.CONNECTED));
    }

    private ConnectionSyncState syncState(User owner, ConnectionEntity connection, SyncCapability capability) {
        return syncStateRepository.findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                        new ConnectionSyncStateId(connection.getId(), owner.getId(), capability), owner.getId())
                .orElseThrow();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor login(User user) {
        return oidcLogin().idToken(token -> token.subject(user.getGoogleSubject())
                .claim("email", user.getEmail()).claim("email_verified", true));
    }

    private static NormalizedAssetBalance balance(String symbol, String quantity) {
        return new NormalizedAssetBalance("SOLANA:MINT:" + symbol, symbol, null,
                NormalizedAssetCategory.CRYPTO, "solana", symbol.equals("SOL") ? "native" : symbol,
                new BigDecimal(quantity), Instant.now());
    }

    private static NormalizedActivity swapActivity() {
        return new NormalizedActivity("signature-swap-1", "solana:signature-swap-1",
                NormalizedActivityType.SWAP, "SWAP", "SUCCESS", ACTIVITY_AT,
                List.of(
                        new NormalizedActivityLeg(0, NormalizedDirection.OUT, "SOLANA:MINT:SOL", "SOL",
                                new BigDecimal("1"), new BigDecimal("1"), "SOL"),
                        new NormalizedActivityLeg(1, NormalizedDirection.IN, "SOLANA:MINT:USDC", "USDC",
                                new BigDecimal("150"), new BigDecimal("150"), "USDC")));
    }

    private static NormalizedActivity depositActivity() {
        return new NormalizedActivity("signature-deposit-1", "solana:signature-deposit-1",
                NormalizedActivityType.DEPOSIT, "TRANSFER", "SUCCESS", ACTIVITY_AT.plusSeconds(60),
                List.of(new NormalizedActivityLeg(0, NormalizedDirection.IN, "SOLANA:MINT:USDC", "USDC",
                        new BigDecimal("20"), new BigDecimal("20"), "USDC")));
    }

    private static NormalizedActivity activityWithDuplicateLegIndexes() {
        NormalizedActivity activity = swapActivity();
        NormalizedActivityLeg first = activity.legs().getFirst();
        NormalizedActivityLeg second = activity.legs().get(1);
        return new NormalizedActivity(activity.providerEventId(), "solana:atomicity-failure",
                activity.eventType(), activity.originalEventType(), activity.status(), activity.occurredAt(),
                List.of(first, new NormalizedActivityLeg(0, second.direction(), second.assetKey(), second.symbol(),
                        second.quantity(), second.originalAmount(), second.originalCurrency())));
    }

    private static Object json(String content, String path) {
        return com.jayway.jsonpath.JsonPath.read(content, path);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> jsonList(String content, String path) {
        return (List<Object>) com.jayway.jsonpath.JsonPath.read(content, path);
    }
}
