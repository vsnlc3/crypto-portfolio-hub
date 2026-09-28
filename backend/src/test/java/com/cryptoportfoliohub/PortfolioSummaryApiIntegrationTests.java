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
import com.cryptoportfoliohub.domain.money.CurrencyCode;
import com.cryptoportfoliohub.domain.money.FxRate;
import com.cryptoportfoliohub.domain.money.Price;
import com.cryptoportfoliohub.marketdata.AssetMarketMapping;
import com.cryptoportfoliohub.marketdata.MarketDataService;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.marketdata.domain.MarketDataSource;
import com.cryptoportfoliohub.marketdata.domain.MarketFxQuote;
import com.cryptoportfoliohub.marketdata.domain.MarketPriceChange;
import com.cryptoportfoliohub.marketdata.domain.MarketPriceQuote;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.UserRepository;
import com.cryptoportfoliohub.portfolio.api.PortfolioSummaryResponse;
import com.cryptoportfoliohub.portfolio.api.PortfolioSummaryStatus;
import com.cryptoportfoliohub.portfolio.domain.ConnectionPortfolioStatus;

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
class PortfolioSummaryApiIntegrationTests {

    private static final Instant EVALUATED_AT = Instant.parse("2026-09-28T02:00:00Z");

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
                MarketPriceQuote quote = switch (assetKey) {
                    case "BTC" -> price("BTC", "100", CurrencyCode.USD);
                    case "USDC" -> price("USDC", "1.02", CurrencyCode.USD);
                    default -> unavailablePrice(assetKey);
                };
                quotes.put(assetKey, quote);
            }
            return Map.copyOf(quotes);
        });
        when(marketDataService.fxRate(any(CurrencyCode.class), eq(CurrencyCode.JPY)))
                .thenAnswer(invocation -> fx(invocation.getArgument(0)));
    }

    @Test
    void summaryIsOwnerScopedAndDoesNotAddPositionValueOrMarginToNetWorth() throws Exception {
        User owner = createUser("summary-owner");
        User other = createUser("summary-other");
        Fixture bitbank = createBitbankFixture(owner, "owner-bitbank", new BigDecimal("2"));
        Fixture hyperliquid = createHyperliquidFixture(owner, "owner-hyperliquid");
        createBitbankFixture(other, "other-bitbank", new BigDecimal("99"));
        insertSyncState(bitbank, owner, "ACTIVITY", "ERROR", null);

        PortfolioSummaryResponse response = getSummary(owner);

        assertThat(response.summary().status()).isEqualTo(PortfolioSummaryStatus.COMPLETE);
        assertThat(response.summary().netWorthJpy()).isEqualByComparingTo("46800.00000000");
        assertThat(response.summary().holdingsValueJpy()).isEqualByComparingTo("45300.00000000");
        assertThat(response.summary().directionalValueJpy()).isEqualByComparingTo("30000.00000000");
        assertThat(response.summary().stablecoinValueJpy()).isEqualByComparingTo("15300.00000000");
        assertThat(response.summary().marketExposureJpy()).isEqualByComparingTo("32800.00000000");
        assertThat(response.summary().unrealizedPnlJpy()).isEqualByComparingTo("-150.00000000");
        assertThat(response.summary().netWorthJpy())
                .isNotEqualByComparingTo("50050.00000000"); // Holdings + equity + Position Value + Margin.
        assertThat(response.summary().change24h().amountJpy()).isNull();
        assertThat(response.summary().change24h().percentage()).isNull();
        assertThat(response.summary().change24h().status()).isEqualTo(PortfolioSummaryStatus.UNAVAILABLE);
        assertThat(response.connections()).hasSize(2);
        assertThat(response.connections()).extracting(PortfolioSummaryResponse.Connection::id)
                .containsExactlyInAnyOrder(bitbank.connectionId(), hyperliquid.connectionId());
        assertThat(response.connections()).noneMatch(connection ->
                connection.netWorthJpy() != null && connection.netWorthJpy().compareTo(new BigDecimal("1485000")) == 0);
        assertThat(response.summary().lastSuccessfulSyncAt()).isEqualTo(EVALUATED_AT);
        assertThat(response.connections().stream()
                .filter(connection -> connection.id().equals(bitbank.connectionId()))
                .findFirst().orElseThrow().capabilitySync())
                .anySatisfy(capability -> {
                    assertThat(capability.capability().name()).isEqualTo("ACTIVITY");
                    assertThat(capability.status().name()).isEqualTo("ERROR");
                });
        assertThat(hyperliquid.connectionId()).isNotNull();
    }

    @Test
    void reportsConnectionPartialStaleAndUnavailableWithoutReplacingUnknownWithZero() throws Exception {
        User owner = createUser("summary-statuses");
        Fixture stale = createBitbankFixture(owner, "stale", BigDecimal.ONE);
        Fixture partial = createBitbankFixture(owner, "partial", BigDecimal.ONE);
        Fixture unavailable = createConnection(owner, "unavailable", "BITBANK");
        insertBalance(partial, owner, "UNKNOWN:CUSTOM", "CUSTOM", "CUSTOM", BigDecimal.ONE);
        jdbcTemplate.update("""
                UPDATE connection_sync_states
                SET status = 'ERROR', last_error_category = 'UNAVAILABLE'
                WHERE connection_id = ? AND user_id = ? AND capability = 'BALANCE'
                """, stale.connectionId(), owner.getId());

        PortfolioSummaryResponse response = getSummary(owner);

        assertThat(response.summary().status()).isEqualTo(PortfolioSummaryStatus.PARTIAL);
        assertThat(response.summary().netWorthJpy()).isNull();
        assertThat(connection(response, stale.connectionId()).dataStatus()).isEqualTo(ConnectionPortfolioStatus.STALE);
        assertThat(connection(response, partial.connectionId()).dataStatus()).isEqualTo(ConnectionPortfolioStatus.PARTIAL);
        assertThat(connection(response, unavailable.connectionId()).dataStatus())
                .isEqualTo(ConnectionPortfolioStatus.UNAVAILABLE);
        assertThat(connection(response, unavailable.connectionId()).netWorthJpy()).isNull();
    }

    @Test
    void noConnectionsReturnsUnavailableAndNullAmounts() throws Exception {
        User owner = createUser("summary-empty");

        PortfolioSummaryResponse response = getSummary(owner);

        assertThat(response.summary().status()).isEqualTo(PortfolioSummaryStatus.UNAVAILABLE);
        assertThat(response.summary().netWorthJpy()).isNull();
        assertThat(response.summary().holdingsValueJpy()).isNull();
        assertThat(response.connections()).isEmpty();
    }

    @Test
    void summaryEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/summary"))
                .andExpect(status().isUnauthorized());
    }

    private PortfolioSummaryResponse getSummary(User user) throws Exception {
        String json = mockMvc.perform(get("/api/v1/portfolio/summary").with(login(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, PortfolioSummaryResponse.class);
    }

    private PortfolioSummaryResponse.Connection connection(PortfolioSummaryResponse response, UUID id) {
        return response.connections().stream().filter(connection -> connection.id().equals(id))
                .findFirst().orElseThrow();
    }

    private Fixture createBitbankFixture(User user, String suffix, BigDecimal quantity) {
        Fixture fixture = createConnection(user, suffix, "BITBANK");
        insertSyncState(fixture, user, "BALANCE", "READY", fixture.runId());
        insertBalance(fixture, user, "BTC", "BTC", "BITBANK", quantity);
        return fixture;
    }

    private Fixture createHyperliquidFixture(User user, String suffix) {
        Fixture fixture = createConnection(user, suffix, "HYPERLIQUID");
        insertSyncState(fixture, user, "BALANCE", "READY", fixture.runId());
        insertSyncState(fixture, user, "POSITION", "READY", fixture.runId());
        insertSyncState(fixture, user, "ACCOUNT", "READY", fixture.runId());
        UUID balanceId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO asset_balances (
                    id, connection_id, user_id, asset_key, symbol, asset_category, network, asset_ref,
                    total_quantity, valuation_status, fetched_at, last_success_sync_run_id
                ) VALUES (?, ?, ?, ?, 'USDC', 'CRYPTO', 'HYPERLIQUID', ?, 100, 'UNAVAILABLE', ?, ?)
                """, balanceId, fixture.connectionId(), user.getId(),
                "HYPERLIQUID:SPOT:" + AssetMarketMapping.HYPERLIQUID_USDC_TOKEN_ID,
                AssetMarketMapping.HYPERLIQUID_USDC_TOKEN_ID, timestamp(), fixture.runId());
        jdbcTemplate.update("""
                INSERT INTO provider_account_states (
                    id, connection_id, user_id, account_scope, account_mode, provider_abstraction_mode,
                    account_currency, account_equity, equity_includes_unrealized_pnl,
                    fetched_at, last_success_sync_run_id
                ) VALUES
                    (?, ?, ?, 'ACCOUNT', 'STANDARD', 'disabled', NULL, NULL, NULL, ?, ?),
                    (?, ?, ?, 'PERP_DEX:DEFAULT', 'STANDARD', 'disabled', 'USDC', 10, true, ?, ?)
                """, UUID.randomUUID(), fixture.connectionId(), user.getId(), timestamp(), fixture.runId(),
                UUID.randomUUID(), fixture.connectionId(), user.getId(), timestamp(), fixture.runId());
        jdbcTemplate.update("""
                INSERT INTO perpetual_positions (
                    id, connection_id, user_id, position_key, instrument_code, side, quantity,
                    entry_price, mark_price, price_currency, margin_amount, margin_currency,
                    unrealized_pnl, pnl_currency, fetched_at, last_success_sync_run_id
                ) VALUES (?, ?, ?, 'DEFAULT:BTC', 'BTC', 'LONG', 2, 9, 10, 'USDT', 3, 'USDC', -1, 'USDC', ?, ?)
                """, UUID.randomUUID(), fixture.connectionId(), user.getId(), timestamp(), fixture.runId());
        return fixture;
    }

    private Fixture createConnection(User user, String suffix, String provider) {
        UUID connectionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO connections (id, user_id, provider, display_name, status)
                VALUES (?, ?, ?, ?, 'CONNECTED')
                """, connectionId, user.getId(), provider, suffix);
        jdbcTemplate.update("""
                INSERT INTO sync_runs (id, connection_id, user_id, trigger_type, status, started_at, finished_at)
                VALUES (?, ?, ?, 'INITIAL', 'SUCCESS', ?, ?)
                """, runId, connectionId, user.getId(), timestamp(), timestamp());
        return new Fixture(connectionId, runId);
    }

    private void insertSyncState(Fixture fixture, User user, String capability, String status, UUID runId) {
        jdbcTemplate.update("""
                INSERT INTO connection_sync_states (
                    connection_id, user_id, capability, status, last_attempt_at, last_success_at,
                    last_success_sync_run_id, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, fixture.connectionId(), user.getId(), capability, status,
                timestamp(), runId == null ? null : timestamp(), runId, timestamp());
    }

    private void insertBalance(
            Fixture fixture, User user, String assetKey, String symbol, String network, BigDecimal quantity) {
        jdbcTemplate.update("""
                INSERT INTO asset_balances (
                    id, connection_id, user_id, asset_key, symbol, asset_category, network,
                    total_quantity, valuation_status, fetched_at, last_success_sync_run_id
                ) VALUES (?, ?, ?, ?, ?, 'CRYPTO', ?, ?, 'UNAVAILABLE', ?, ?)
                """, UUID.randomUUID(), fixture.connectionId(), user.getId(), assetKey, symbol, network,
                quantity, timestamp(), fixture.runId());
    }

    private User createUser(String prefix) {
        return userRepository.saveAndFlush(new User(
                prefix + "-" + UUID.randomUUID(), prefix + "@example.test", "Summary Test User", null));
    }

    private RequestPostProcessor login(User user) {
        return oidcLogin().idToken(token -> token.subject(user.getGoogleSubject())
                .claim("email", user.getEmail()).claim("email_verified", true));
    }

    private static Timestamp timestamp() {
        return Timestamp.from(EVALUATED_AT);
    }

    private static MarketPriceQuote price(String asset, String amount, CurrencyCode currency) {
        return new MarketPriceQuote(asset, Optional.of(Price.of(asset, amount, currency)),
                Optional.of(new MarketPriceChange(BigDecimal.ZERO,
                        MarketPriceChange.Unit.PERCENTAGE, MarketPriceChange.ComparisonPeriod.H24)),
                Optional.of(MarketDataSource.COINGECKO), Optional.of(EVALUATED_AT),
                DataFreshness.FRESH, Optional.empty());
    }

    private static MarketPriceQuote unavailablePrice(String asset) {
        return new MarketPriceQuote(asset, Optional.empty(), Optional.empty(),
                Optional.of(MarketDataSource.COINGECKO), Optional.empty(),
                DataFreshness.UNAVAILABLE, Optional.empty());
    }

    private static MarketFxQuote fx(CurrencyCode currency) {
        if (CurrencyCode.JPY.equals(currency)) {
            return new MarketFxQuote(currency, CurrencyCode.JPY,
                    Optional.of(FxRate.identity(CurrencyCode.JPY)), Optional.of(MarketDataSource.IDENTITY),
                    Optional.empty(), DataFreshness.FRESH, Optional.empty());
        }
        BigDecimal rate = currency.equals(CurrencyCode.USD) || currency.equals(new CurrencyCode("USDC"))
                ? new BigDecimal("150") : new BigDecimal("140");
        return new MarketFxQuote(currency, CurrencyCode.JPY,
                Optional.of(new FxRate(currency, CurrencyCode.JPY, rate)),
                Optional.of(currency.equals(CurrencyCode.USD)
                        ? MarketDataSource.EXCHANGERATE_API : MarketDataSource.COINGECKO_AND_EXCHANGERATE_API),
                Optional.of(EVALUATED_AT), DataFreshness.FRESH, Optional.empty());
    }

    private record Fixture(UUID connectionId, UUID runId) {
    }
}
