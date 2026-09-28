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
import com.cryptoportfoliohub.marketdata.MarketDataService;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.marketdata.domain.MarketDataSource;
import com.cryptoportfoliohub.marketdata.domain.MarketFxQuote;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.UserRepository;
import com.cryptoportfoliohub.positions.api.PositionDataStatus;
import com.cryptoportfoliohub.positions.api.PositionsResponse;

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
class PositionsApiIntegrationTests {

    private static final Instant EVALUATED_AT = Instant.parse("2026-09-28T03:00:00Z");
    private final Map<String, MarketFxQuote> fxQuotes = new LinkedHashMap<>();

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
        fxQuotes.clear();
        fxQuotes.put("USD", fx("USD", "150", DataFreshness.FRESH));
        fxQuotes.put("USDC", fx("USDC", "140", DataFreshness.FRESH));
        fxQuotes.put("USDT", fx("USDT", "130", DataFreshness.FRESH));
        when(marketDataService.currentPrices(anyCollection())).thenReturn(Map.of());
        when(marketDataService.fxRate(any(CurrencyCode.class), eq(CurrencyCode.JPY)))
                .thenAnswer(invocation -> fxQuotes.getOrDefault(
                        ((CurrencyCode) invocation.getArgument(0)).value(),
                        unavailableFx((CurrencyCode) invocation.getArgument(0))));
    }

    @Test
    void returnsPositionFieldsAndUsesPriceMarginAndPnlFxIndependentlyForItsOwner() throws Exception {
        User owner = createUser("positions-owner");
        User other = createUser("positions-other");
        createHyperliquidFixture(owner, "BTC", "2", "3", "-4");
        createHyperliquidFixture(other, "ETH", "50", "8", "12");

        PositionsResponse response = getPositions(owner);

        assertThat(response.positions()).hasSize(1);
        PositionsResponse.Position position = response.positions().getFirst();
        assertThat(position.instrumentCode()).isEqualTo("BTC");
        assertThat(position.side()).hasToString("LONG");
        assertThat(position.leverage()).isEqualByComparingTo("2");
        assertThat(position.quantity()).isEqualByComparingTo("2");
        assertThat(position.entryPrice()).isEqualByComparingTo("90");
        assertThat(position.markPrice()).isEqualByComparingTo("100");
        assertThat(position.liquidationPrice()).isEqualByComparingTo("50");
        assertThat(position.priceCurrency()).isEqualTo("USD");
        assertThat(position.positionValueJpy()).isEqualByComparingTo("30000.00000000");
        assertThat(position.marginAmount()).isEqualByComparingTo("3");
        assertThat(position.marginCurrency()).isEqualTo("USDC");
        assertThat(position.marginJpy()).isEqualByComparingTo("420.00000000");
        assertThat(position.unrealizedPnl()).isEqualByComparingTo("-4");
        assertThat(position.pnlCurrency()).isEqualTo("USDT");
        assertThat(position.unrealizedPnlJpy()).isEqualByComparingTo("-520.00000000");
        assertThat(position.priceFx().currency()).isEqualTo("USD");
        assertThat(position.priceFx().rateToJpy()).isEqualByComparingTo("150");
        assertThat(position.priceFx().source()).isEqualTo("EXCHANGERATE_API");
        assertThat(position.marginFx().currency()).isEqualTo("USDC");
        assertThat(position.marginFx().rateToJpy()).isEqualByComparingTo("140");
        assertThat(position.pnlFx().currency()).isEqualTo("USDT");
        assertThat(position.pnlFx().rateToJpy()).isEqualByComparingTo("130");
        assertThat(position.status()).isEqualTo(PositionDataStatus.COMPLETE);
        assertThat(response.summary().positionValueJpy()).isEqualByComparingTo("30000.00000000");
        assertThat(response.summary().marginJpy()).isEqualByComparingTo("420.00000000");
        assertThat(response.summary().unrealizedPnlJpy()).isEqualByComparingTo("-520.00000000");
        assertThat(response.summary().status()).isEqualTo(PositionDataStatus.COMPLETE);
        assertThat(response.summary().connectionCount()).isEqualTo(1);
    }

    @Test
    void unavailablePnlFxReturnsNullInsteadOfZeroAndKeepsOtherValuations() throws Exception {
        User owner = createUser("positions-pnl-fx-unavailable");
        createHyperliquidFixture(owner, "BTC", "2", "3", "-4");
        fxQuotes.put("USDT", unavailableFx(new CurrencyCode("USDT")));

        PositionsResponse response = getPositions(owner);

        PositionsResponse.Position position = response.positions().getFirst();
        assertThat(position.positionValueJpy()).isEqualByComparingTo("30000.00000000");
        assertThat(position.marginJpy()).isEqualByComparingTo("420.00000000");
        assertThat(position.pnlCurrency()).isEqualTo("USDT");
        assertThat(position.unrealizedPnlJpy()).isNull();
        assertThat(position.pnlFx().rateToJpy()).isNull();
        assertThat(position.pnlFx().status()).isEqualTo(PositionDataStatus.UNAVAILABLE);
        assertThat(position.status()).isEqualTo(PositionDataStatus.PARTIAL);
        assertThat(response.summary().unrealizedPnlJpy()).isNull();
        assertThat(response.summary().status()).isEqualTo(PositionDataStatus.PARTIAL);
    }

    @Test
    void staleMarginFxKeepsItsJpyValueAndMarksThePositionStale() throws Exception {
        User owner = createUser("positions-margin-fx-stale");
        createHyperliquidFixture(owner, "BTC", "2", "3", "-4");
        fxQuotes.put("USDC", fx("USDC", "140", DataFreshness.STALE));

        PositionsResponse response = getPositions(owner);

        PositionsResponse.Position position = response.positions().getFirst();
        assertThat(position.marginJpy()).isEqualByComparingTo("420.00000000");
        assertThat(position.marginFx().status()).isEqualTo(PositionDataStatus.STALE);
        assertThat(position.status()).isEqualTo(PositionDataStatus.STALE);
        assertThat(response.summary().status()).isEqualTo(PositionDataStatus.STALE);
    }

    @Test
    void positionsEndpointRequiresAuthenticationAndNoConnectionsDoNotCreateZeroTotals() throws Exception {
        mockMvc.perform(get("/api/v1/positions"))
                .andExpect(status().isUnauthorized());

        User owner = createUser("positions-no-connections");
        PositionsResponse response = getPositions(owner);
        assertThat(response.positions()).isEmpty();
        assertThat(response.summary().status()).isEqualTo(PositionDataStatus.UNAVAILABLE);
        assertThat(response.summary().positionValueJpy()).isNull();
        assertThat(response.summary().marginJpy()).isNull();
        assertThat(response.summary().unrealizedPnlJpy()).isNull();
    }

    private PositionsResponse getPositions(User user) throws Exception {
        String json = mockMvc.perform(get("/api/v1/positions").with(login(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, PositionsResponse.class);
    }

    private Fixture createHyperliquidFixture(User user, String instrument, String quantity,
            String marginAmount, String pnlAmount) {
        UUID connectionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String address = "0x" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO connections (id, user_id, provider, display_name, external_account_ref, status)
                VALUES (?, ?, 'HYPERLIQUID', 'Test Hyperliquid', ?, 'CONNECTED')
                """, connectionId, user.getId(), address);
        jdbcTemplate.update("""
                INSERT INTO sync_runs (id, connection_id, user_id, trigger_type, status, started_at, finished_at)
                VALUES (?, ?, ?, 'INITIAL', 'SUCCESS', ?, ?)
                """, runId, connectionId, user.getId(), Timestamp.from(EVALUATED_AT), Timestamp.from(EVALUATED_AT));
        for (String capability : new String[] {"BALANCE", "POSITION", "ACCOUNT"}) {
            jdbcTemplate.update("""
                    INSERT INTO connection_sync_states (
                        connection_id, user_id, capability, status, last_attempt_at,
                        last_success_at, last_success_sync_run_id, updated_at)
                    VALUES (?, ?, ?, 'READY', ?, ?, ?, ?)
                    """, connectionId, user.getId(), capability, Timestamp.from(EVALUATED_AT),
                    Timestamp.from(EVALUATED_AT), runId, Timestamp.from(EVALUATED_AT));
        }
        jdbcTemplate.update("""
                INSERT INTO perpetual_positions (
                    id, connection_id, user_id, position_key, instrument_code, side, quantity,
                    entry_price, mark_price, liquidation_price, price_currency, leverage,
                    margin_amount, margin_currency, unrealized_pnl, pnl_currency,
                    fetched_at, last_success_sync_run_id)
                VALUES (?, ?, ?, ?, ?, 'LONG', ?, 90, 100, 50, 'USD', 2, ?, 'USDC', ?, 'USDT', ?, ?)
                """, UUID.randomUUID(), connectionId, user.getId(), "DEFAULT:" + instrument,
                instrument, new BigDecimal(quantity), new BigDecimal(marginAmount), new BigDecimal(pnlAmount),
                Timestamp.from(EVALUATED_AT), runId);
        return new Fixture(connectionId, runId);
    }

    private User createUser(String prefix) {
        return userRepository.saveAndFlush(new User(
                prefix + "-" + UUID.randomUUID(), prefix + "@example.test", "Position Test User", null));
    }

    private RequestPostProcessor login(User user) {
        return oidcLogin().idToken(token -> token.subject(user.getGoogleSubject())
                .claim("email", user.getEmail()).claim("email_verified", true));
    }

    private static MarketFxQuote fx(String currency, String rate, DataFreshness freshness) {
        CurrencyCode from = new CurrencyCode(currency);
        MarketDataSource source = currency.equals("USD")
                ? MarketDataSource.EXCHANGERATE_API
                : MarketDataSource.COINGECKO_AND_EXCHANGERATE_API;
        return new MarketFxQuote(from, CurrencyCode.JPY,
                Optional.of(new FxRate(from, CurrencyCode.JPY, new BigDecimal(rate))),
                Optional.of(source), Optional.of(EVALUATED_AT), freshness, Optional.empty());
    }

    private static MarketFxQuote unavailableFx(CurrencyCode currency) {
        return new MarketFxQuote(currency, CurrencyCode.JPY, Optional.empty(),
                Optional.of(MarketDataSource.EXCHANGERATE_API), Optional.empty(),
                DataFreshness.UNAVAILABLE, Optional.empty());
    }

    private record Fixture(UUID connectionId, UUID runId) {
    }
}
