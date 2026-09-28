package com.cryptoportfoliohub;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;
import com.cryptoportfoliohub.assets.api.AssetDataStatus;
import com.cryptoportfoliohub.assets.api.AssetsResponse;
import com.cryptoportfoliohub.domain.money.CurrencyCode;
import com.cryptoportfoliohub.domain.money.FxRate;
import com.cryptoportfoliohub.domain.money.Price;
import com.cryptoportfoliohub.marketdata.MarketDataService;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.marketdata.domain.MarketDataSource;
import com.cryptoportfoliohub.marketdata.domain.MarketFxQuote;
import com.cryptoportfoliohub.marketdata.domain.MarketPriceChange;
import com.cryptoportfoliohub.marketdata.domain.MarketPriceQuote;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class AssetsApiIntegrationTests {

    private static final Instant EVALUATED_AT = Instant.parse("2026-09-28T02:00:00Z");
    private static final String SOLANA_NATIVE_REF = "NATIVE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private MarketDataService marketDataService;

    @BeforeEach
    void setUpMarketData() {
        when(marketDataService.currentPrices(anyCollection())).thenAnswer(invocation -> {
            Collection<String> assetKeys = invocation.getArgument(0);
            Map<String, MarketPriceQuote> quotes = new LinkedHashMap<>();
            for (String assetKey : assetKeys) {
                quotes.put(assetKey, assetKey.equals("SOL") ? solQuote(true) : unavailableQuote(assetKey));
            }
            return Map.copyOf(quotes);
        });
        when(marketDataService.fxRate(any(CurrencyCode.class), eq(CurrencyCode.JPY)))
                .thenAnswer(invocation -> fxQuote(invocation.getArgument(0)));
    }

    @Test
    void aggregatesCanonicalAssetAcrossOwnedConnectionsAndReturnsQuoteMetadata() throws Exception {
        User owner = createUser("assets-owner");
        User other = createUser("assets-other");
        createSolanaFixture(owner, "wallet-one", new BigDecimal("0.5"), true);
        createSolanaFixture(owner, "wallet-two", new BigDecimal("1.5"), true);
        createSolanaFixture(other, "wallet-other", new BigDecimal("99"), true);

        AssetsResponse response = getAssets(owner);

        AssetsResponse.Asset sol = response.assets().stream()
                .filter(asset -> asset.symbol().equals("SOL"))
                .findFirst().orElseThrow();
        assertThat(sol.totalQuantity()).isEqualByComparingTo("2.0");
        assertThat(sol.valueJpy()).isEqualByComparingTo("30000.00000000");
        assertThat(sol.status()).isEqualTo(AssetDataStatus.COMPLETE);
        assertThat(sol.price().amount()).isEqualByComparingTo("100");
        assertThat(sol.price().currency()).isEqualTo("USD");
        assertThat(sol.price().source()).isEqualTo("COINGECKO");
        assertThat(sol.price().evaluatedAt()).isEqualTo(EVALUATED_AT);
        assertThat(sol.change24h().value()).isEqualByComparingTo("2.75");
        assertThat(sol.change24h().unit()).isEqualTo("PERCENTAGE");
        assertThat(sol.change24h().comparisonPeriod()).isEqualTo("H24");
        assertThat(sol.change24h().source()).isEqualTo("COINGECKO");
        assertThat(sol.change24h().evaluatedAt()).isEqualTo(EVALUATED_AT);
        assertThat(sol.change24h().status()).isEqualTo(AssetDataStatus.COMPLETE);
        assertThat(sol.connections()).hasSize(2);
        assertThat(sol.connections().stream()
                        .map(holding -> holding.quantity().stripTrailingZeros()).toList())
                .containsExactlyInAnyOrder(new BigDecimal("0.5"), new BigDecimal("1.5"));
        assertThat(response.summary().spotHoldingsValueJpy()).isEqualByComparingTo("30000.00000000");
        assertThat(response.summary().directionalAssetsValueJpy()).isEqualByComparingTo("30000.00000000");
        assertThat(response.summary().stablecoinsValueJpy()).isEqualByComparingTo("0");
        assertThat(response.summary().status()).isEqualTo(AssetDataStatus.COMPLETE);
        assertThat(response.summary().connectionCount()).isEqualTo(2);
        assertThat(response.summary().syncedConnectionCount()).isEqualTo(2);
        assertThat(response.assets()).noneMatch(asset -> asset.totalQuantity().compareTo(new BigDecimal("99")) == 0);
    }

    @Test
    void missingTwentyFourHourChangeIsNullAndUnavailableInsteadOfZero() throws Exception {
        User owner = createUser("assets-no-change");
        createSolanaFixture(owner, "wallet-no-change", BigDecimal.ONE, true);
        when(marketDataService.currentPrices(anyCollection())).thenReturn(Map.of("SOL", solQuote(false)));

        AssetsResponse response = getAssets(owner);

        AssetsResponse.Asset sol = response.assets().stream()
                .filter(asset -> asset.symbol().equals("SOL"))
                .findFirst().orElseThrow();
        assertThat(sol.change24h().value()).isNull();
        assertThat(sol.change24h().status()).isEqualTo(AssetDataStatus.UNAVAILABLE);
        assertThat(sol.change24h().unit()).isEqualTo("PERCENTAGE");
        assertThat(sol.change24h().comparisonPeriod()).isEqualTo("H24");
    }

    @Test
    void staleMarketQuoteMarksAssetAndSummaryStale() throws Exception {
        User owner = createUser("assets-stale-market");
        createSolanaFixture(owner, "wallet-stale-market", BigDecimal.ONE, true);
        when(marketDataService.currentPrices(anyCollection())).thenReturn(Map.of("SOL", staleSolQuote()));

        AssetsResponse response = getAssets(owner);

        AssetsResponse.Asset sol = response.assets().stream()
                .filter(asset -> asset.symbol().equals("SOL"))
                .findFirst().orElseThrow();
        assertThat(response.summary().status()).isEqualTo(AssetDataStatus.STALE);
        assertThat(sol.status()).isEqualTo(AssetDataStatus.STALE);
        assertThat(sol.price().status()).isEqualTo(AssetDataStatus.STALE);
        assertThat(sol.change24h().value()).isNull();
        assertThat(sol.change24h().status()).isEqualTo(AssetDataStatus.STALE);
    }

    @Test
    void unavailableFxLeavesJpyValueNullWhileKeepingTheMarketQuote() throws Exception {
        User owner = createUser("assets-missing-fx");
        createSolanaFixture(owner, "wallet-missing-fx", BigDecimal.ONE, true);
        when(marketDataService.fxRate(eq(CurrencyCode.USD), eq(CurrencyCode.JPY)))
                .thenReturn(new MarketFxQuote(CurrencyCode.USD, CurrencyCode.JPY, Optional.empty(),
                        Optional.of(MarketDataSource.EXCHANGERATE_API), Optional.empty(),
                        DataFreshness.UNAVAILABLE, Optional.empty()));

        AssetsResponse response = getAssets(owner);

        AssetsResponse.Asset sol = response.assets().stream()
                .filter(asset -> asset.symbol().equals("SOL"))
                .findFirst().orElseThrow();
        assertThat(response.summary().status()).isEqualTo(AssetDataStatus.UNAVAILABLE);
        assertThat(response.summary().spotHoldingsValueJpy()).isNull();
        assertThat(sol.valueJpy()).isNull();
        assertThat(sol.status()).isEqualTo(AssetDataStatus.UNAVAILABLE);
        assertThat(sol.price().amount()).isEqualByComparingTo("100");
    }

    @Test
    void neverSyncedConnectionMakesPortfolioAssetTotalsUnavailableNotZero() throws Exception {
        User owner = createUser("assets-unsynced");
        createSolanaFixture(owner, "wallet-ready", BigDecimal.ONE, true);
        createSolanaFixture(owner, "wallet-uninitialized", BigDecimal.ONE, false);

        AssetsResponse response = getAssets(owner);

        assertThat(response.summary().status()).isEqualTo(AssetDataStatus.PARTIAL);
        assertThat(response.summary().spotHoldingsValueJpy()).isNull();
        assertThat(response.summary().directionalAssetsValueJpy()).isNull();
        assertThat(response.summary().syncedConnectionCount()).isEqualTo(1);
        AssetsResponse.Asset sol = response.assets().stream()
                .filter(asset -> asset.symbol().equals("SOL"))
                .findFirst().orElseThrow();
        assertThat(sol.totalQuantity()).isNull();
        assertThat(sol.valueJpy()).isNull();
        assertThat(sol.status()).isEqualTo(AssetDataStatus.PARTIAL);
    }

    @Test
    void sameUnknownSymbolWithDifferentTokenReferencesStaysSeparated() throws Exception {
        User owner = createUser("assets-unknown-tokens");
        Fixture fixture = createSolanaFixture(owner, "wallet-tokens", BigDecimal.ONE, true);
        insertBalance(fixture, owner, "TOKEN:mint-one", "ABC", "mint-one", new BigDecimal("2"));
        insertBalance(fixture, owner, "TOKEN:mint-two", "ABC", "mint-two", new BigDecimal("3"));

        AssetsResponse response = getAssets(owner);

        assertThat(response.assets().stream()
                        .filter(asset -> asset.symbol().equals("ABC"))
                        .map(asset -> asset.totalQuantity().stripTrailingZeros()).toList())
                .hasSize(2)
                .containsExactlyInAnyOrder(new BigDecimal("2"), new BigDecimal("3"));
    }

    @Test
    void noConnectionsDoesNotReportKnownZeroPortfolio() throws Exception {
        User owner = createUser("assets-no-connections");

        AssetsResponse response = getAssets(owner);

        assertThat(response.summary().status()).isEqualTo(AssetDataStatus.UNAVAILABLE);
        assertThat(response.summary().connectionCount()).isZero();
        assertThat(response.summary().spotHoldingsValueJpy()).isNull();
        assertThat(response.assets()).isEmpty();
    }

    @Test
    void assetsEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/assets"))
                .andExpect(status().isUnauthorized());
    }

    private AssetsResponse getAssets(User user) throws Exception {
        String json = mockMvc.perform(get("/api/v1/assets").with(login(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, AssetsResponse.class);
    }

    private Fixture createSolanaFixture(User user, String suffix, BigDecimal quantity, boolean ready) {
        UUID connectionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant now = EVALUATED_AT;
        jdbcTemplate.update("""
                INSERT INTO connections (id, user_id, provider, display_name, external_account_ref, status)
                VALUES (?, ?, 'SOLANA', ?, ?, 'CONNECTED')
                """, connectionId, user.getId(), suffix, "wallet-address-" + suffix);
        jdbcTemplate.update("""
                INSERT INTO sync_runs (id, connection_id, user_id, trigger_type, status, started_at, finished_at)
                VALUES (?, ?, ?, 'INITIAL', 'SUCCESS', ?, ?)
                """, runId, connectionId, user.getId(), Timestamp.from(now), Timestamp.from(now));
        if (ready) {
            jdbcTemplate.update("""
                    INSERT INTO connection_sync_states (
                        connection_id, user_id, capability, status, last_attempt_at,
                        last_success_at, last_success_sync_run_id, updated_at)
                    VALUES (?, ?, 'BALANCE', 'READY', ?, ?, ?, ?)
                    """, connectionId, user.getId(), Timestamp.from(now), Timestamp.from(now),
                    runId, Timestamp.from(now));
        }
        Fixture fixture = new Fixture(connectionId, runId, user.getId());
        insertBalance(fixture, user, "SOL", "SOL", SOLANA_NATIVE_REF, quantity);
        return fixture;
    }

    private void insertBalance(
            Fixture fixture, User user, String assetKey, String symbol, String assetRef, BigDecimal quantity) {
        jdbcTemplate.update("""
                INSERT INTO asset_balances (
                    id, connection_id, user_id, asset_key, symbol, asset_name, asset_category,
                    network, asset_ref, total_quantity, valuation_status, fetched_at, last_success_sync_run_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'SOLANA', ?, ?, 'UNAVAILABLE', ?, ?)
                """, UUID.randomUUID(), fixture.connectionId(), user.getId(), assetKey, symbol, symbol,
                "SOL".equals(symbol) ? "CRYPTO" : "CRYPTO", assetRef, quantity,
                Timestamp.from(EVALUATED_AT), fixture.runId());
    }

    private User createUser(String prefix) {
        return userRepository.saveAndFlush(new User(
                prefix + "-" + UUID.randomUUID(), prefix + "@example.test", "Asset Test User", null));
    }

    private RequestPostProcessor login(User user) {
        return oidcLogin().idToken(token -> token.subject(user.getGoogleSubject())
                .claim("email", user.getEmail()).claim("email_verified", true));
    }

    private static MarketPriceQuote solQuote(boolean withChange) {
        Optional<MarketPriceChange> change = withChange
                ? Optional.of(new MarketPriceChange(new BigDecimal("2.75"),
                        MarketPriceChange.Unit.PERCENTAGE, MarketPriceChange.ComparisonPeriod.H24))
                : Optional.empty();
        return new MarketPriceQuote("SOL", Optional.of(Price.of("SOL", "100", CurrencyCode.USD)),
                change, Optional.of(MarketDataSource.COINGECKO), Optional.of(EVALUATED_AT),
                DataFreshness.FRESH, Optional.empty());
    }

    private static MarketPriceQuote unavailableQuote(String assetKey) {
        return new MarketPriceQuote(assetKey, Optional.empty(), Optional.empty(),
                Optional.of(MarketDataSource.COINGECKO), Optional.empty(),
                DataFreshness.UNAVAILABLE, Optional.empty());
    }

    private static MarketPriceQuote staleSolQuote() {
        return new MarketPriceQuote("SOL", Optional.of(Price.of("SOL", "100", CurrencyCode.USD)),
                Optional.empty(), Optional.of(MarketDataSource.COINGECKO), Optional.of(EVALUATED_AT),
                DataFreshness.STALE, Optional.empty());
    }

    private static MarketFxQuote fxQuote(CurrencyCode fromCurrency) {
        if (CurrencyCode.JPY.equals(fromCurrency)) {
            return new MarketFxQuote(CurrencyCode.JPY, CurrencyCode.JPY,
                    Optional.of(FxRate.identity(CurrencyCode.JPY)), Optional.of(MarketDataSource.IDENTITY),
                    Optional.empty(), DataFreshness.FRESH, Optional.empty());
        }
        return new MarketFxQuote(fromCurrency, CurrencyCode.JPY,
                Optional.of(new FxRate(fromCurrency, CurrencyCode.JPY, new BigDecimal("150"))),
                Optional.of(MarketDataSource.EXCHANGERATE_API), Optional.of(EVALUATED_AT),
                DataFreshness.FRESH, Optional.empty());
    }

    private record Fixture(UUID connectionId, UUID runId, UUID userId) {
    }
}
