package com.cryptoportfoliohub.marketdata;

import com.cryptoportfoliohub.domain.money.CurrencyCode;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.marketdata.coingecko.CoinGeckoClient;
import com.cryptoportfoliohub.marketdata.coingecko.CoinGeckoPriceObservation;
import com.cryptoportfoliohub.marketdata.config.MarketDataProperties;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.marketdata.domain.MarketDataSource;
import com.cryptoportfoliohub.marketdata.domain.MarketPriceChange;
import com.cryptoportfoliohub.marketdata.exchangerate.ExchangeRateApiClient;
import com.cryptoportfoliohub.marketdata.exchangerate.FxObservation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataServiceTests {

    private static final Instant START = Instant.parse("2026-09-27T12:00:00Z");

    private final CoinGeckoClient coinGeckoClient = mock(CoinGeckoClient.class);
    private final ExchangeRateApiClient exchangeRateApiClient = mock(ExchangeRateApiClient.class);
    private final MarketDataProperties properties = new MarketDataProperties();
    private final MutableClock clock = new MutableClock(START);
    private MarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        properties.setCoinGeckoDemoApiKey("test-only-key");
        properties.setExchangeRateApiKey("test-only-key");
        marketDataService = new MarketDataService(coinGeckoClient, exchangeRateApiClient, properties, clock);
    }

    @Test
    void mapsCanonicalAssetsAndSharesOneBatchedPriceFetchAcrossRequests() {
        when(coinGeckoClient.fetchPrices(CoinGeckoAssetMapping.allCoinIds())).thenReturn(Map.of(
                "bitcoin", new CoinGeckoPriceObservation(new BigDecimal("64250.123456789012345"),
                        Optional.of(new BigDecimal("1.4")), START.minusSeconds(60)),
                "solana", new CoinGeckoPriceObservation(new BigDecimal("151.25"), START.minusSeconds(30))));

        var first = marketDataService.currentPrices(List.of("BTC", "SOL"));
        var second = marketDataService.currentPrice("BTC");

        assertThat(first.get("BTC").price()).contains(com.cryptoportfoliohub.domain.money.Price.of(
                "BTC", "64250.123456789012345", CurrencyCode.USD));
        assertThat(first.get("BTC").source()).contains(MarketDataSource.COINGECKO);
        assertThat(first.get("BTC").evaluatedAt()).contains(START.minusSeconds(60));
        assertThat(first.get("BTC").freshness()).isEqualTo(DataFreshness.FRESH);
        assertThat(first.get("BTC").change24h()).contains(new MarketPriceChange(
                new BigDecimal("1.4"),
                MarketPriceChange.Unit.PERCENTAGE,
                MarketPriceChange.ComparisonPeriod.H24));
        assertThat(second.price()).isEqualTo(first.get("BTC").price());
        verify(coinGeckoClient).fetchPrices(List.of("bitcoin", "ethereum", "solana", "ripple", "hyperliquid", "usd-coin"));
    }

    @Test
    void returnsUnsupportedAssetsAsUnavailableWithoutProviderRequests() {
        var quote = marketDataService.currentPrice("UNKNOWN:COIN");

        assertThat(quote.price()).isEmpty();
        assertThat(quote.source()).isEmpty();
        assertThat(quote.freshness()).isEqualTo(DataFreshness.UNAVAILABLE);
        verify(coinGeckoClient, never()).fetchPrices(anyCollection());
    }

    @Test
    void keepsCurrentPriceWhen24hChangeIsMissing() {
        Instant evaluatedAt = START.minusSeconds(20);
        when(coinGeckoClient.fetchPrices(CoinGeckoAssetMapping.allCoinIds())).thenReturn(Map.of(
                "bitcoin", new CoinGeckoPriceObservation(BigDecimal.TEN, evaluatedAt)));

        var quote = marketDataService.currentPrice("BTC");

        assertThat(quote.price()).isPresent();
        assertThat(quote.change24h()).isEmpty();
        assertThat(quote.source()).contains(MarketDataSource.COINGECKO);
        assertThat(quote.evaluatedAt()).contains(evaluatedAt);
        assertThat(quote.freshness()).isEqualTo(DataFreshness.FRESH);
    }

    @Test
    void missingApiKeyLeavesPricesUnavailableAndDoesNotPreventServiceCreation() {
        properties.setCoinGeckoDemoApiKey("");

        var quote = marketDataService.currentPrice("BTC");

        assertThat(quote.price()).isEmpty();
        assertThat(quote.source()).contains(MarketDataSource.COINGECKO);
        assertThat(quote.failureCategory()).isEmpty();
        verify(coinGeckoClient, never()).fetchPrices(anyCollection());
    }

    @Test
    void keepsLastKnownPriceAndMarksItStaleAfterRefreshRateLimit() {
        when(coinGeckoClient.fetchPrices(CoinGeckoAssetMapping.allCoinIds()))
                .thenReturn(Map.of("bitcoin", new CoinGeckoPriceObservation(BigDecimal.TEN, START.minus(Duration.ofMinutes(1)))))
                .thenThrow(new ProviderException(ProviderErrorCategory.RATE_LIMIT));

        assertThat(marketDataService.currentPrice("BTC").freshness()).isEqualTo(DataFreshness.FRESH);
        clock.advance(Duration.ofMinutes(16));

        var staleQuote = marketDataService.currentPrice("BTC");

        assertThat(staleQuote.price()).isPresent();
        assertThat(staleQuote.freshness()).isEqualTo(DataFreshness.STALE);
        assertThat(staleQuote.change24h()).isEmpty();
        assertThat(staleQuote.failureCategory()).contains(ProviderErrorCategory.RATE_LIMIT);
    }

    @Test
    void retriesFailedPriceRefreshOnlyAfterBoundedBackoff() {
        when(coinGeckoClient.fetchPrices(CoinGeckoAssetMapping.allCoinIds()))
                .thenThrow(new ProviderException(ProviderErrorCategory.UNAVAILABLE));

        marketDataService.currentPrice("BTC");
        marketDataService.currentPrice("ETH");
        verify(coinGeckoClient).fetchPrices(CoinGeckoAssetMapping.allCoinIds());

        clock.advance(Duration.ofMinutes(1));
        marketDataService.currentPrice("BTC");
        verify(coinGeckoClient, org.mockito.Mockito.times(2)).fetchPrices(CoinGeckoAssetMapping.allCoinIds());
    }

    @Test
    void appliesUsdJpyProviderRateAndRetainsItsUpdateTimeAndFreshness() {
        Instant updatedAt = START.minus(Duration.ofHours(24));
        when(exchangeRateApiClient.fetchUsdToJpy())
                .thenReturn(new FxObservation(new BigDecimal("149.4567890123"), updatedAt));

        var quote = marketDataService.fxRate(CurrencyCode.USD, CurrencyCode.JPY);

        assertThat(quote.rate().orElseThrow().rate()).isEqualByComparingTo("149.4567890123");
        assertThat(quote.source()).contains(MarketDataSource.EXCHANGERATE_API);
        assertThat(quote.evaluatedAt()).contains(updatedAt);
        assertThat(quote.freshness()).isEqualTo(DataFreshness.FRESH);
    }

    @Test
    void jpyIdentityRateIsOneAndDoesNotCallFxProvider() {
        var quote = marketDataService.fxRate(CurrencyCode.JPY, CurrencyCode.JPY);

        assertThat(quote.rate().orElseThrow().rate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(quote.source()).contains(MarketDataSource.IDENTITY);
        assertThat(quote.evaluatedAt()).isEmpty();
        assertThat(quote.freshness()).isEqualTo(DataFreshness.FRESH);
        verify(exchangeRateApiClient, never()).fetchUsdToJpy();
    }

    @Test
    void unsupportedFxAndMissingKeyAreUnavailableRatherThanZero() {
        var unsupported = marketDataService.fxRate(new CurrencyCode("EUR"), CurrencyCode.JPY);
        assertThat(unsupported.rate()).isEmpty();
        assertThat(unsupported.freshness()).isEqualTo(DataFreshness.UNAVAILABLE);

        properties.setExchangeRateApiKey("");
        var missingKey = marketDataService.fxRate(CurrencyCode.USD, CurrencyCode.JPY);
        assertThat(missingKey.rate()).isEmpty();
        assertThat(missingKey.source()).contains(MarketDataSource.EXCHANGERATE_API);
        assertThat(missingKey.freshness()).isEqualTo(DataFreshness.UNAVAILABLE);
        verify(exchangeRateApiClient, never()).fetchUsdToJpy();
    }

    @Test
    void retainsFxAfterProviderFailureAndMarksItStaleBeyondFreshnessLimit() {
        Instant updatedAt = START.minus(Duration.ofHours(71));
        when(exchangeRateApiClient.fetchUsdToJpy())
                .thenReturn(new FxObservation(new BigDecimal("150"), updatedAt))
                .thenThrow(new ProviderException(ProviderErrorCategory.TIMEOUT));

        assertThat(marketDataService.fxRate(CurrencyCode.USD, CurrencyCode.JPY).freshness())
                .isEqualTo(DataFreshness.FRESH);
        clock.advance(Duration.ofHours(49));

        var staleQuote = marketDataService.fxRate(CurrencyCode.USD, CurrencyCode.JPY);

        assertThat(staleQuote.rate()).isPresent();
        assertThat(staleQuote.freshness()).isEqualTo(DataFreshness.STALE);
        assertThat(staleQuote.failureCategory()).contains(ProviderErrorCategory.TIMEOUT);
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        private void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
