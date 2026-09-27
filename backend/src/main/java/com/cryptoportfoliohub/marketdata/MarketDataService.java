package com.cryptoportfoliohub.marketdata;

import com.cryptoportfoliohub.domain.money.CurrencyCode;
import com.cryptoportfoliohub.domain.money.FxRate;
import com.cryptoportfoliohub.domain.money.Price;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.marketdata.coingecko.CoinGeckoClient;
import com.cryptoportfoliohub.marketdata.coingecko.CoinGeckoPriceObservation;
import com.cryptoportfoliohub.marketdata.config.MarketDataProperties;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.marketdata.domain.MarketDataSource;
import com.cryptoportfoliohub.marketdata.domain.MarketFxQuote;
import com.cryptoportfoliohub.marketdata.domain.MarketPriceQuote;
import com.cryptoportfoliohub.marketdata.exchangerate.ExchangeRateApiClient;
import com.cryptoportfoliohub.marketdata.exchangerate.FxObservation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MarketDataService {

    private static final Duration INITIAL_RETRY_BACKOFF = Duration.ofMinutes(1);
    private static final Duration MAX_RETRY_BACKOFF = Duration.ofMinutes(30);

    private final CoinGeckoClient coinGeckoClient;
    private final ExchangeRateApiClient exchangeRateApiClient;
    private final MarketDataProperties properties;
    private final Clock clock;

    private Map<String, CoinGeckoPriceObservation> cachedPrices = Map.of();
    private Instant nextPriceRefresh = Instant.MIN;
    private Instant nextPriceAttempt = Instant.MIN;
    private int priceFailureCount;
    private Optional<ProviderErrorCategory> lastPriceFailure = Optional.empty();

    private Optional<FxObservation> cachedUsdJpy = Optional.empty();
    private Instant nextFxRefresh = Instant.MIN;
    private Instant nextFxAttempt = Instant.MIN;
    private int fxFailureCount;
    private Optional<ProviderErrorCategory> lastFxFailure = Optional.empty();

    public MarketDataService(
            CoinGeckoClient coinGeckoClient,
            ExchangeRateApiClient exchangeRateApiClient,
            MarketDataProperties properties,
            Clock clock) {
        this.coinGeckoClient = coinGeckoClient;
        this.exchangeRateApiClient = exchangeRateApiClient;
        this.properties = properties;
        this.clock = clock;
    }

    public MarketPriceQuote currentPrice(String assetKey) {
        return currentPrices(List.of(assetKey)).get(assetKey);
    }

    public Map<String, MarketPriceQuote> currentPrices(Collection<String> assetKeys) {
        Objects.requireNonNull(assetKeys, "assetKeys must not be null");
        LinkedHashMap<String, MarketPriceQuote> quotes = new LinkedHashMap<>();
        for (String assetKey : assetKeys) {
            Objects.requireNonNull(assetKey, "assetKey must not be null");
            quotes.putIfAbsent(assetKey, null);
        }

        boolean hasSupportedAsset = quotes.keySet().stream().anyMatch(key -> CoinGeckoAssetMapping.coinIdFor(key).isPresent());
        if (hasSupportedAsset) {
            refreshPricesIfNeeded();
        }
        Map<String, CoinGeckoPriceObservation> observations = cachedPrices;
        Optional<ProviderErrorCategory> failure = lastPriceFailure;
        Instant now = clock.instant();
        quotes.replaceAll((assetKey, ignored) -> priceQuote(assetKey, observations, failure, now));
        return Map.copyOf(quotes);
    }

    public MarketFxQuote fxRate(CurrencyCode fromCurrency, CurrencyCode toCurrency) {
        Objects.requireNonNull(fromCurrency, "fromCurrency must not be null");
        Objects.requireNonNull(toCurrency, "toCurrency must not be null");

        if (CurrencyCode.JPY.equals(fromCurrency) && CurrencyCode.JPY.equals(toCurrency)) {
            return new MarketFxQuote(
                    fromCurrency,
                    toCurrency,
                    Optional.of(FxRate.identity(CurrencyCode.JPY)),
                    Optional.of(MarketDataSource.IDENTITY),
                    Optional.empty(),
                    DataFreshness.FRESH,
                    Optional.empty());
        }
        if (!CurrencyCode.USD.equals(fromCurrency) || !CurrencyCode.JPY.equals(toCurrency)) {
            return unavailableFx(fromCurrency, toCurrency, Optional.empty(), Optional.empty());
        }

        refreshFxIfNeeded();
        Optional<FxObservation> observation = cachedUsdJpy;
        Optional<ProviderErrorCategory> failure = lastFxFailure;
        if (observation.isEmpty()) {
            return unavailableFx(fromCurrency, toCurrency, Optional.of(MarketDataSource.EXCHANGERATE_API), failure);
        }

        FxObservation value = observation.orElseThrow();
        DataFreshness freshness = isStale(value.evaluatedAt(), properties.getFxFreshness(), clock.instant())
                ? DataFreshness.STALE
                : DataFreshness.FRESH;
        return new MarketFxQuote(
                fromCurrency,
                toCurrency,
                Optional.of(new FxRate(fromCurrency, toCurrency, value.rate())),
                Optional.of(MarketDataSource.EXCHANGERATE_API),
                Optional.of(value.evaluatedAt()),
                freshness,
                failure);
    }

    private synchronized void refreshPricesIfNeeded() {
        Instant now = clock.instant();
        if (properties.getCoinGeckoDemoApiKey().isBlank()
                || now.isBefore(nextPriceRefresh)
                || now.isBefore(nextPriceAttempt)) {
            return;
        }
        try {
            Map<String, CoinGeckoPriceObservation> response = coinGeckoClient.fetchPrices(CoinGeckoAssetMapping.allCoinIds());
            Map<String, CoinGeckoPriceObservation> merged = new HashMap<>(cachedPrices);
            response.forEach((coinId, price) -> {
                if (CoinGeckoAssetMapping.allCoinIds().contains(coinId)) {
                    merged.put(coinId, price);
                }
            });
            cachedPrices = Map.copyOf(merged);
            nextPriceRefresh = now.plus(properties.getPriceCacheTtl());
            nextPriceAttempt = nextPriceRefresh;
            priceFailureCount = 0;
            lastPriceFailure = Optional.empty();
        } catch (ProviderException exception) {
            priceFailureCount++;
            nextPriceRefresh = Instant.MIN;
            nextPriceAttempt = now.plus(retryBackoff(priceFailureCount));
            lastPriceFailure = Optional.of(exception.category());
        }
    }

    private synchronized void refreshFxIfNeeded() {
        Instant now = clock.instant();
        if (properties.getExchangeRateApiKey().isBlank()
                || now.isBefore(nextFxRefresh)
                || now.isBefore(nextFxAttempt)) {
            return;
        }
        try {
            FxObservation response = exchangeRateApiClient.fetchUsdToJpy();
            cachedUsdJpy = Optional.of(response);
            nextFxRefresh = max(
                    now.plus(properties.getFxMaximumRequestInterval()),
                    response.evaluatedAt().plus(properties.getFxMaximumRequestInterval()));
            nextFxAttempt = nextFxRefresh;
            fxFailureCount = 0;
            lastFxFailure = Optional.empty();
        } catch (ProviderException exception) {
            fxFailureCount++;
            nextFxRefresh = Instant.MIN;
            nextFxAttempt = now.plus(retryBackoff(fxFailureCount));
            lastFxFailure = Optional.of(exception.category());
        }
    }

    private MarketPriceQuote priceQuote(
            String assetKey,
            Map<String, CoinGeckoPriceObservation> observations,
            Optional<ProviderErrorCategory> failure,
            Instant now) {
        Optional<String> coinId = CoinGeckoAssetMapping.coinIdFor(assetKey);
        if (coinId.isEmpty()) {
            return new MarketPriceQuote(
                    assetKey, Optional.empty(), Optional.empty(), Optional.empty(), DataFreshness.UNAVAILABLE, Optional.empty());
        }
        Optional<CoinGeckoPriceObservation> observation = Optional.ofNullable(observations.get(coinId.orElseThrow()));
        if (observation.isEmpty()) {
            return new MarketPriceQuote(
                    assetKey,
                    Optional.empty(),
                    Optional.of(MarketDataSource.COINGECKO),
                    Optional.empty(),
                    DataFreshness.UNAVAILABLE,
                    failure);
        }
        CoinGeckoPriceObservation value = observation.orElseThrow();
        return new MarketPriceQuote(
                assetKey,
                Optional.of(Price.of(assetKey, value.amount().toPlainString(), CurrencyCode.USD)),
                Optional.of(MarketDataSource.COINGECKO),
                Optional.of(value.evaluatedAt()),
                isStale(value.evaluatedAt(), properties.getPriceFreshness(), now)
                        ? DataFreshness.STALE
                        : DataFreshness.FRESH,
                failure);
    }

    private static MarketFxQuote unavailableFx(
            CurrencyCode fromCurrency,
            CurrencyCode toCurrency,
            Optional<MarketDataSource> source,
            Optional<ProviderErrorCategory> failure) {
        return new MarketFxQuote(
                fromCurrency,
                toCurrency,
                Optional.empty(),
                source,
                Optional.empty(),
                DataFreshness.UNAVAILABLE,
                failure);
    }

    private static boolean isStale(Instant evaluatedAt, Duration maxAge, Instant now) {
        return evaluatedAt.isAfter(now) || Duration.between(evaluatedAt, now).compareTo(maxAge) > 0;
    }

    private static Instant max(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static Duration retryBackoff(int failureCount) {
        int exponent = Math.min(Math.max(failureCount - 1, 0), 5);
        Duration backoff = INITIAL_RETRY_BACKOFF.multipliedBy(1L << exponent);
        return backoff.compareTo(MAX_RETRY_BACKOFF) > 0 ? MAX_RETRY_BACKOFF : backoff;
    }
}
