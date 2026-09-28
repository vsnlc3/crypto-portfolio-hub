package com.cryptoportfoliohub;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.cryptoportfoliohub.domain.money.CurrencyCode;
import com.cryptoportfoliohub.domain.money.FxRate;
import com.cryptoportfoliohub.domain.money.Price;
import com.cryptoportfoliohub.marketdata.AssetMarketMapping;
import com.cryptoportfoliohub.marketdata.MarketDataService;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.marketdata.domain.MarketDataSource;
import com.cryptoportfoliohub.marketdata.domain.MarketFxQuote;
import com.cryptoportfoliohub.marketdata.domain.MarketPriceQuote;
import com.cryptoportfoliohub.marketdata.domain.MarketPriceChange;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.portfolio.application.PortfolioValuationService;
import com.cryptoportfoliohub.portfolio.application.PortfolioSnapshotService;
import com.cryptoportfoliohub.portfolio.domain.ConnectionPortfolioStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class PortfolioValuationIntegrationTests {

    private static final Instant EVALUATED_AT = Instant.parse("2026-09-28T01:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PortfolioValuationService valuationService;

    @Autowired
    private PortfolioSnapshotService snapshotService;

    @MockitoBean
    private MarketDataService marketDataService;

    @BeforeEach
    void setUpMarketData() {
        when(marketDataService.currentPrices(anyCollection())).thenReturn(Map.of(
                "BTC", price("BTC", "100", CurrencyCode.USD),
                "USDC", price("USDC", "1.02", CurrencyCode.USD)));
        when(marketDataService.fxRate(eq(CurrencyCode.USD), eq(CurrencyCode.JPY)))
                .thenReturn(fx(CurrencyCode.USD, "150"));
        when(marketDataService.fxRate(eq(new CurrencyCode("USDT")), eq(CurrencyCode.JPY)))
                .thenReturn(fx(new CurrencyCode("USDT"), "140"));
        when(marketDataService.fxRate(eq(new CurrencyCode("USDC")), eq(CurrencyCode.JPY)))
                .thenReturn(fx(new CurrencyCode("USDC"), "150"));
    }

    @Test
    void valuesOnlyTheAuthenticatedUsersBalancesAndPersistsPriceAndFxInputs() {
        UUID userA = createUser("portfolio-a");
        UUID userB = createUser("portfolio-b");
        Fixture fixtureA = createBitbankFixture(userA, new BigDecimal("2"));
        Fixture fixtureB = createBitbankFixture(userB, new BigDecimal("3"));
        UUID jpyBalance = insertJpyBalance(fixtureA, userA, new BigDecimal("1234.5"));

        var resultA = valuationService.valueUser(userA);

        assertThat(resultA.netWorthJpy()).contains(new BigDecimal("31234.50000000"));
        assertThat(resultA.holdingsValueJpy()).contains(new BigDecimal("31234.50000000"));
        assertThat(resultA.directionalValueJpy()).contains(new BigDecimal("30000.00000000"));
        assertThat(resultA.freshness()).isEqualTo(DataFreshness.FRESH);
        assertBalanceValuation(fixtureA.balanceId(), "VALUED", "30000.00000000");
        Map<String, Object> btcRow = jdbcTemplate.queryForMap(
                "SELECT price_source, fx_source, fx_rate_to_jpy FROM asset_balances WHERE id = ?",
                fixtureA.balanceId());
        assertThat(btcRow.get("price_source")).isEqualTo("COINGECKO");
        assertThat(btcRow.get("fx_source")).isEqualTo("EXCHANGERATE_API");
        assertThat(btcRow.get("fx_rate_to_jpy")).isEqualTo(new BigDecimal("150.000000000000"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT price_evaluated_at = ? FROM asset_balances WHERE id = ?", Boolean.class,
                timestamp(), fixtureA.balanceId())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT fx_evaluated_at = ? FROM asset_balances WHERE id = ?", Boolean.class,
                timestamp(), fixtureA.balanceId())).isTrue();
        Map<String, Object> jpyRow = jdbcTemplate.queryForMap(
                "SELECT jpy_value, price_source, fx_source FROM asset_balances WHERE id = ?", jpyBalance);
        assertThat(jpyRow.get("jpy_value")).isEqualTo(new BigDecimal("1234.50000000"));
        assertThat(jpyRow.get("price_source")).isEqualTo("IDENTITY");
        assertThat(jpyRow.get("fx_source")).isEqualTo("IDENTITY");
        assertBalanceValuation(fixtureB.balanceId(), "UNAVAILABLE", null);

        var resultB = valuationService.valueUser(userB);
        assertThat(resultB.netWorthJpy()).contains(new BigDecimal("45000.00000000"));
        assertBalanceValuation(fixtureB.balanceId(), "VALUED", "45000.00000000");
    }

    @Test
    void usesSeparatePriceMarginAndPnlFxAndDoesNotDoubleCountStandardAccountPnl() {
        UUID user = createUser("portfolio-hyperliquid");
        Fixture fixture = createHyperliquidFixture(user, true);

        var result = valuationService.valueUser(user);

        assertThat(result.holdingsValueJpy()).contains(new BigDecimal("15300.00000000"));
        assertThat(result.netWorthJpy()).contains(new BigDecimal("16800.00000000"));
        assertThat(result.positionValueJpy()).contains(new BigDecimal("2800.00000000"));
        assertThat(result.marketExposureJpy()).contains(new BigDecimal("2800.00000000"));
        assertThat(result.marginJpy()).contains(new BigDecimal("450.00000000"));
        assertThat(result.unrealizedPnlJpy()).contains(new BigDecimal("-150.00000000"));

        Map<String, Object> position = jdbcTemplate.queryForMap(
                "SELECT price_fx_rate_to_jpy, margin_fx_rate_to_jpy, pnl_fx_rate_to_jpy, "
                        + "price_fx_source, margin_fx_source, pnl_fx_source "
                        + "FROM perpetual_positions WHERE id = ?", fixture.positionId());
        assertThat(position.get("price_fx_rate_to_jpy")).isEqualTo(new BigDecimal("140.000000000000"));
        assertThat(position.get("margin_fx_rate_to_jpy")).isEqualTo(new BigDecimal("150.000000000000"));
        assertThat(position.get("pnl_fx_rate_to_jpy")).isEqualTo(new BigDecimal("150.000000000000"));
        assertThat(position.get("price_fx_source")).isEqualTo("COINGECKO_AND_EXCHANGERATE_API");
        assertThat(position.get("margin_fx_source")).isEqualTo("COINGECKO_AND_EXCHANGERATE_API");
        assertThat(position.get("pnl_fx_source")).isEqualTo("COINGECKO_AND_EXCHANGERATE_API");

        Map<String, Object> account = jdbcTemplate.queryForMap(
                "SELECT fx_rate_to_jpy, fx_source, account_equity_jpy FROM provider_account_states "
                        + "WHERE connection_id = ? AND account_scope = 'PERP_DEX:DEFAULT'", fixture.connectionId());
        assertThat(account.get("fx_rate_to_jpy")).isEqualTo(new BigDecimal("150.000000000000"));
        assertThat(account.get("fx_source")).isEqualTo("COINGECKO_AND_EXCHANGERATE_API");
        assertThat(account.get("account_equity_jpy")).isEqualTo(new BigDecimal("1500.00000000"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT fx_evaluated_at = ? FROM provider_account_states "
                        + "WHERE connection_id = ? AND account_scope = 'PERP_DEX:DEFAULT'", Boolean.class,
                timestamp(), fixture.connectionId())).isTrue();
    }

    @Test
    void addsPositionPnlOnceWhenProviderEquityDoesNotIncludeIt() {
        UUID user = createUser("portfolio-equity-excludes-pnl");
        createHyperliquidFixture(user, false);

        var result = valuationService.valueUser(user);

        assertThat(result.netWorthJpy()).contains(new BigDecimal("16650.00000000"));
    }

    @Test
    void connectionContributionsSumToNetWorthWithoutAddingPositionValueOrMargin() {
        UUID user = createUser("connection-portfolio-total");
        Fixture bitbank = createBitbankFixture(user, new BigDecimal("2"));
        Fixture hyperliquid = createHyperliquidFixture(user, true);

        var result = valuationService.valueUserWithConnections(user);

        assertThat(result.portfolio().netWorthJpy()).contains(new BigDecimal("46800.00000000"));
        assertThat(result.connections()).hasSize(2);
        assertThat(result.connections().get(bitbank.connectionId()).amountJpy())
                .contains(new BigDecimal("30000.00000000"));
        assertThat(result.connections().get(hyperliquid.connectionId()).amountJpy())
                .contains(new BigDecimal("16800.00000000"));
        assertThat(result.connections().values())
                .allSatisfy(value -> assertThat(value.status()).isEqualTo(ConnectionPortfolioStatus.COMPLETE));
    }

    @Test
    void connectionValueIsUnavailableAsPartialInsteadOfReturningAUsablePartialSum() {
        UUID user = createUser("connection-portfolio-partial");
        Fixture fixture = createBitbankFixture(user, new BigDecimal("2"));
        jdbcTemplate.update("""
                INSERT INTO asset_balances (
                    id, connection_id, user_id, asset_key, symbol, asset_category, network,
                    total_quantity, valuation_status, fetched_at, last_success_sync_run_id
                ) VALUES (?, ?, ?, 'CUSTOM:UNKNOWN', 'UNKNOWN', 'CRYPTO', 'UNSUPPORTED', 1,
                    'UNAVAILABLE', ?, ?)
                """, UUID.randomUUID(), fixture.connectionId(), user, timestamp(), fixture.syncRunId());

        var result = valuationService.valueUserWithConnections(user);
        var connectionValue = result.connections().get(fixture.connectionId());

        assertThat(result.portfolio().netWorthJpy()).isEmpty();
        assertThat(connectionValue.amountJpy()).isEmpty();
        assertThat(connectionValue.status()).isEqualTo(ConnectionPortfolioStatus.PARTIAL);
    }

    @Test
    void connectionValueRetainsAStaleAmountFromTheLastSuccessfulCurrentState() {
        UUID user = createUser("connection-portfolio-stale");
        Fixture fixture = createBitbankFixture(user, new BigDecimal("2"));
        jdbcTemplate.update("""
                UPDATE connection_sync_states
                SET status = 'ERROR', last_error_category = 'UNAVAILABLE'
                WHERE connection_id = ? AND user_id = ? AND capability = 'BALANCE'
                """, fixture.connectionId(), user);

        var connectionValue = valuationService.valueUserWithConnections(user)
                .connections().get(fixture.connectionId());

        assertThat(connectionValue.amountJpy()).contains(new BigDecimal("30000.00000000"));
        assertThat(connectionValue.status()).isEqualTo(ConnectionPortfolioStatus.STALE);
    }

    @Test
    void leavesKnownQuantityAndJpyValueUnavailableWhenFxCannotBeFetched() {
        UUID user = createUser("portfolio-fx-unavailable");
        Fixture fixture = createBitbankFixture(user, new BigDecimal("2"));
        when(marketDataService.fxRate(eq(CurrencyCode.USD), eq(CurrencyCode.JPY))).thenReturn(
                new MarketFxQuote(CurrencyCode.USD, CurrencyCode.JPY, Optional.empty(),
                        Optional.of(MarketDataSource.EXCHANGERATE_API), Optional.empty(),
                        DataFreshness.UNAVAILABLE, Optional.empty()));

        var result = valuationService.valueUser(user);

        assertThat(result.netWorthJpy()).isEmpty();
        assertBalanceValuation(fixture.balanceId(), "UNAVAILABLE", null);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_quantity FROM asset_balances WHERE id = ?", BigDecimal.class, fixture.balanceId()))
                .isEqualByComparingTo("2");
    }

    @Test
    void leavesKnownQuantityAndJpyValueUnavailableWhenPriceCannotBeFetched() {
        UUID user = createUser("portfolio-price-unavailable");
        Fixture fixture = createBitbankFixture(user, new BigDecimal("2"));
        when(marketDataService.currentPrices(anyCollection())).thenReturn(Map.of());

        var result = valuationService.valueUser(user);

        assertThat(result.netWorthJpy()).isEmpty();
        assertBalanceValuation(fixture.balanceId(), "UNAVAILABLE", null);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_quantity FROM asset_balances WHERE id = ?", BigDecimal.class, fixture.balanceId()))
                .isEqualByComparingTo("2");
    }

    @Test
    void keepsKnownStalePriceAndFxValuationsMarkedStale() {
        UUID user = createUser("portfolio-stale-market-data");
        Fixture fixture = createBitbankFixture(user, new BigDecimal("2"));
        Instant staleAt = EVALUATED_AT.minusSeconds(4 * 86_400);
        when(marketDataService.currentPrices(anyCollection())).thenReturn(Map.of("BTC",
                new MarketPriceQuote("BTC", Optional.of(Price.of("BTC", "100", CurrencyCode.USD)),
                        Optional.<MarketPriceChange>empty(), Optional.of(MarketDataSource.COINGECKO),
                        Optional.of(staleAt), DataFreshness.STALE, Optional.empty())));
        when(marketDataService.fxRate(eq(CurrencyCode.USD), eq(CurrencyCode.JPY))).thenReturn(
                new MarketFxQuote(CurrencyCode.USD, CurrencyCode.JPY,
                        Optional.of(new FxRate(CurrencyCode.USD, CurrencyCode.JPY, new BigDecimal("150"))),
                        Optional.of(MarketDataSource.EXCHANGERATE_API), Optional.of(staleAt),
                        DataFreshness.STALE, Optional.empty()));

        var result = valuationService.valueUser(user);

        assertThat(result.netWorthJpy()).contains(new BigDecimal("30000.00000000"));
        assertThat(result.freshness()).isEqualTo(DataFreshness.STALE);
        assertBalanceValuation(fixture.balanceId(), "VALUED", "30000.00000000");
    }

    @Test
    void usesThePersistedFxPrecisionForTheJpyAmount() {
        UUID user = createUser("portfolio-fx-precision");
        Fixture fixture = createBitbankFixture(user, new BigDecimal("2"));
        when(marketDataService.fxRate(eq(CurrencyCode.USD), eq(CurrencyCode.JPY))).thenReturn(
                new MarketFxQuote(CurrencyCode.USD, CurrencyCode.JPY,
                        Optional.of(new FxRate(CurrencyCode.USD, CurrencyCode.JPY,
                                new BigDecimal("150.123456789012345"))),
                        Optional.of(MarketDataSource.EXCHANGERATE_API), Optional.of(EVALUATED_AT),
                        DataFreshness.FRESH, Optional.empty()));

        var result = valuationService.valueUser(user);

        assertThat(result.netWorthJpy()).contains(new BigDecimal("30024.69135780"));
        Map<String, Object> valuation = jdbcTemplate.queryForMap(
                "SELECT fx_rate_to_jpy, jpy_value FROM asset_balances WHERE id = ?", fixture.balanceId());
        assertThat(valuation.get("fx_rate_to_jpy")).isEqualTo(new BigDecimal("150.123456789012"));
        assertThat(valuation.get("jpy_value")).isEqualTo(new BigDecimal("30024.69135780"));
    }

    @Test
    void savesCompleteSnapshotOnceAndIgnoresActivityCapabilityFailure() {
        UUID user = createUser("snapshot-complete");
        Fixture fixture = createBitbankFixture(user, new BigDecimal("2"));
        jdbcTemplate.update("""
                INSERT INTO connection_sync_states (
                    connection_id, user_id, capability, status, last_attempt_at,
                    last_success_at, last_success_sync_run_id, last_error_category, updated_at
                ) VALUES (?, ?, 'ACTIVITY', 'ERROR', ?, NULL, NULL, 'UNAVAILABLE', ?)
                """, fixture.connectionId(), user, timestamp(), timestamp());

        assertThat(snapshotService.createSnapshotIfEligible(user)).isTrue();
        assertThat(snapshotService.createSnapshotIfEligible(user)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM portfolio_snapshots WHERE user_id = ?", Integer.class, user)).isEqualTo(1);
        Map<String, Object> snapshot = jdbcTemplate.queryForMap(
                "SELECT net_worth_jpy, holdings_value_jpy, directional_value_jpy, stablecoin_value_jpy, "
                        + "market_exposure_jpy, unrealized_pnl_jpy, status, data_as_of_at "
                        + "FROM portfolio_snapshots WHERE user_id = ?", user);
        assertThat(snapshot.get("net_worth_jpy")).isEqualTo(new BigDecimal("30000.00000000"));
        assertThat(snapshot.get("holdings_value_jpy")).isEqualTo(new BigDecimal("30000.00000000"));
        assertThat(snapshot.get("directional_value_jpy")).isEqualTo(new BigDecimal("30000.00000000"));
        assertThat(snapshot.get("stablecoin_value_jpy")).isEqualTo(BigDecimal.ZERO.setScale(8));
        assertThat(snapshot.get("market_exposure_jpy")).isEqualTo(new BigDecimal("30000.00000000"));
        assertThat(snapshot.get("unrealized_pnl_jpy")).isEqualTo(BigDecimal.ZERO.setScale(8));
        assertThat(snapshot.get("status")).isEqualTo("COMPLETE");
        assertThat(snapshot.get("data_as_of_at")).isEqualTo(timestamp());
    }

    @Test
    void createsStaleSnapshotFromPriorSuccessfulCurrentState() {
        UUID user = createUser("snapshot-stale");
        Fixture fixture = createBitbankFixture(user, new BigDecimal("2"));
        jdbcTemplate.update("""
                UPDATE connection_sync_states SET status = 'ERROR', last_error_category = 'UNAVAILABLE'
                WHERE connection_id = ? AND user_id = ? AND capability = 'BALANCE'
                """, fixture.connectionId(), user);

        assertThat(snapshotService.createSnapshotIfEligible(user)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM portfolio_snapshots WHERE user_id = ?", String.class, user)).isEqualTo("STALE");
    }

    @Test
    void doesNotCreateSnapshotsWithoutACompletePortfolioOrForAnotherUsersPortfolio() {
        UUID user = createUser("snapshot-no-connections");
        UUID unsyncedUser = createUser("snapshot-not-synced");
        UUID otherUser = createUser("snapshot-other-user");
        assertThat(snapshotService.createSnapshotIfEligible(user)).isFalse();

        Fixture unsynced = createBitbankFixture(unsyncedUser, new BigDecimal("2"));
        jdbcTemplate.update("DELETE FROM connection_sync_states WHERE connection_id = ? AND capability = 'BALANCE'",
                unsynced.connectionId());
        assertThat(snapshotService.createSnapshotIfEligible(unsyncedUser)).isFalse();

        Fixture fixture = createBitbankFixture(otherUser, new BigDecimal("2"));
        when(marketDataService.currentPrices(anyCollection())).thenReturn(Map.of());
        assertThat(snapshotService.createSnapshotIfEligible(user)).isFalse();
        assertThat(snapshotService.createSnapshotIfEligible(unsyncedUser)).isFalse();
        assertThat(snapshotService.createSnapshotIfEligible(otherUser)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM portfolio_snapshots WHERE user_id IN (?, ?, ?)", Integer.class,
                user, unsyncedUser, otherUser)).isZero();
        assertThat(fixture.balanceId()).isNotNull();
    }

    @Test
    void doesNotCreateSnapshotWhenARequiredFxRateIsUnavailable() {
        UUID user = createUser("snapshot-fx-unavailable");
        createBitbankFixture(user, new BigDecimal("2"));
        when(marketDataService.fxRate(eq(CurrencyCode.USD), eq(CurrencyCode.JPY))).thenReturn(
                new MarketFxQuote(CurrencyCode.USD, CurrencyCode.JPY, Optional.empty(),
                        Optional.of(MarketDataSource.EXCHANGERATE_API), Optional.empty(),
                        DataFreshness.UNAVAILABLE, Optional.empty()));

        assertThat(snapshotService.createSnapshotIfEligible(user)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM portfolio_snapshots WHERE user_id = ?", Integer.class, user)).isZero();
    }

    @Test
    void treatsAConfirmedZeroBalanceAsACompleteZeroWithoutAQuote() {
        UUID user = createUser("snapshot-known-zero");
        createBitbankFixture(user, BigDecimal.ZERO);
        when(marketDataService.currentPrices(anyCollection())).thenReturn(Map.of());

        assertThat(snapshotService.createSnapshotIfEligible(user)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT net_worth_jpy FROM portfolio_snapshots WHERE user_id = ?", BigDecimal.class, user))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void marginFxUnavailabilityDoesNotBlockSnapshotMetricsThatDoNotUseMargin() {
        UUID user = createUser("snapshot-margin-fx-unavailable");
        Fixture fixture = createHyperliquidFixture(user, true);
        CurrencyCode eur = new CurrencyCode("EUR");
        when(marketDataService.fxRate(eq(eur), eq(CurrencyCode.JPY))).thenReturn(
                new MarketFxQuote(eur, CurrencyCode.JPY, Optional.empty(),
                        Optional.of(MarketDataSource.EXCHANGERATE_API), Optional.empty(),
                        DataFreshness.UNAVAILABLE, Optional.empty()));
        jdbcTemplate.update(
                "UPDATE perpetual_positions SET margin_currency = 'EUR' WHERE id = ?", fixture.positionId());

        var valuation = valuationService.valueUser(user);
        assertThat(valuation.freshness()).isEqualTo(DataFreshness.UNAVAILABLE);
        assertThat(valuation.snapshotFreshness()).isEqualTo(DataFreshness.FRESH);
        assertThat(snapshotService.createSnapshotIfEligible(user)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM portfolio_snapshots WHERE user_id = ?", String.class, user)).isEqualTo("COMPLETE");
    }

    private Fixture createBitbankFixture(UUID user, BigDecimal quantity) {
        UUID connection = createConnection(user, ConnectionProvider.BITBANK);
        UUID run = createSyncRun(connection, user);
        insertSyncState(connection, user, run, "BALANCE");
        UUID balance = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO asset_balances (
                    id, connection_id, user_id, asset_key, symbol, asset_category, network,
                    total_quantity, valuation_status, fetched_at, last_success_sync_run_id
                ) VALUES (?, ?, ?, 'BTC', 'BTC', 'CRYPTO', 'BITBANK', ?, 'UNAVAILABLE', ?, ?)
                """, balance, connection, user, quantity, timestamp(), run);
        return new Fixture(connection, run, balance, null);
    }

    private UUID insertJpyBalance(Fixture fixture, UUID user, BigDecimal quantity) {
        UUID balance = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO asset_balances (
                    id, connection_id, user_id, asset_key, symbol, asset_category, network,
                    total_quantity, valuation_status, fetched_at, last_success_sync_run_id
                ) VALUES (?, ?, ?, 'JPY', 'JPY', 'FIAT', 'BITBANK', ?, 'UNAVAILABLE', ?, ?)
                """, balance, fixture.connectionId(), user, quantity, timestamp(), fixture.syncRunId());
        return balance;
    }

    private Fixture createHyperliquidFixture(UUID user, boolean equityIncludesPnl) {
        UUID connection = createConnection(user, ConnectionProvider.HYPERLIQUID);
        UUID run = createSyncRun(connection, user);
        for (String capability : Set.of("BALANCE", "POSITION", "ACCOUNT")) {
            insertSyncState(connection, user, run, capability);
        }

        UUID balance = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO asset_balances (
                    id, connection_id, user_id, asset_key, symbol, asset_category, network, asset_ref,
                    total_quantity, valuation_status, fetched_at, last_success_sync_run_id
                ) VALUES (?, ?, ?, ?, 'USDC', 'CRYPTO', 'HYPERLIQUID', ?, 100, 'UNAVAILABLE', ?, ?)
                """, balance, connection, user,
                "HYPERLIQUID:SPOT:" + AssetMarketMapping.HYPERLIQUID_USDC_TOKEN_ID,
                AssetMarketMapping.HYPERLIQUID_USDC_TOKEN_ID, timestamp(), run);

        UUID accountStateId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO provider_account_states (
                    id, connection_id, user_id, account_scope, account_mode, provider_abstraction_mode,
                    account_currency, account_equity, equity_includes_unrealized_pnl,
                    fetched_at, last_success_sync_run_id
                ) VALUES
                    (?, ?, ?, 'ACCOUNT', 'STANDARD', 'disabled', NULL, NULL, NULL, ?, ?),
                    (?, ?, ?, 'PERP_DEX:DEFAULT', 'STANDARD', 'disabled', 'USDC', 10, ?, ?, ?)
                """, accountStateId, connection, user, timestamp(), run,
                UUID.randomUUID(), connection, user, equityIncludesPnl, timestamp(), run);
        UUID position = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO perpetual_positions (
                    id, connection_id, user_id, position_key, instrument_code, side, quantity,
                    entry_price, mark_price, price_currency, margin_amount, margin_currency,
                    unrealized_pnl, pnl_currency, fetched_at, last_success_sync_run_id
                ) VALUES (?, ?, ?, 'DEFAULT:BTC', 'BTC', 'LONG', 2, 9, 10, 'USDT', 3, 'USDC', -1, 'USDC', ?, ?)
                """, position, connection, user, timestamp(), run);
        return new Fixture(connection, run, balance, position);
    }

    private UUID createUser(String suffix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, google_subject, email) VALUES (?, ?, ?)",
                id, "portfolio-test-" + suffix + "-" + id, suffix + "@example.invalid");
        return id;
    }

    private UUID createConnection(UUID user, ConnectionProvider provider) {
        UUID connection = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO connections (id, user_id, provider, status)
                VALUES (?, ?, ?, 'CONNECTED')
                """, connection, user, provider.name());
        return connection;
    }

    private UUID createSyncRun(UUID connection, UUID user) {
        UUID run = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO sync_runs (id, connection_id, user_id, trigger_type, status, started_at, finished_at)
                VALUES (?, ?, ?, 'INITIAL', 'SUCCESS', ?, ?)
                """, run, connection, user, timestamp(), timestamp());
        return run;
    }

    private void insertSyncState(UUID connection, UUID user, UUID run, String capability) {
        jdbcTemplate.update("""
                INSERT INTO connection_sync_states (
                    connection_id, user_id, capability, status, last_attempt_at, last_success_at,
                    last_success_sync_run_id, updated_at
                ) VALUES (?, ?, ?, 'READY', ?, ?, ?, ?)
                """, connection, user, capability, timestamp(), timestamp(), run, timestamp());
    }

    private void assertBalanceValuation(UUID id, String status, String jpyValue) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT valuation_status, jpy_value FROM asset_balances WHERE id = ?", id);
        assertThat(row.get("valuation_status")).isEqualTo(status);
        assertThat(row.get("jpy_value")).isEqualTo(jpyValue == null ? null : new BigDecimal(jpyValue));
    }

    private static MarketPriceQuote price(String asset, String amount, CurrencyCode currency) {
        return new MarketPriceQuote(asset, Optional.of(Price.of(asset, amount, currency)),
                Optional.<MarketPriceChange>empty(), Optional.of(MarketDataSource.COINGECKO),
                Optional.of(EVALUATED_AT), DataFreshness.FRESH, Optional.empty());
    }

    private static Timestamp timestamp() {
        return Timestamp.from(EVALUATED_AT);
    }

    private static MarketFxQuote fx(CurrencyCode currency, String rate) {
        return new MarketFxQuote(currency, CurrencyCode.JPY,
                Optional.of(new FxRate(currency, CurrencyCode.JPY, new BigDecimal(rate))),
                Optional.of(currency.equals(CurrencyCode.USD)
                        ? MarketDataSource.EXCHANGERATE_API : MarketDataSource.COINGECKO_AND_EXCHANGERATE_API),
                Optional.of(EVALUATED_AT), DataFreshness.FRESH, Optional.empty());
    }

    private record Fixture(
            UUID connectionId,
            UUID syncRunId,
            UUID balanceId,
            UUID positionId) {
    }
}
