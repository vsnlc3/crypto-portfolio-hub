package com.cryptoportfoliohub.marketdata.domain;

import com.cryptoportfoliohub.domain.money.CurrencyCode;
import com.cryptoportfoliohub.domain.money.FxRate;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record MarketFxQuote(
        CurrencyCode fromCurrency,
        CurrencyCode toCurrency,
        Optional<FxRate> rate,
        Optional<MarketDataSource> source,
        Optional<Instant> evaluatedAt,
        DataFreshness freshness,
        Optional<ProviderErrorCategory> failureCategory) {

    public MarketFxQuote {
        Objects.requireNonNull(fromCurrency, "fromCurrency must not be null");
        Objects.requireNonNull(toCurrency, "toCurrency must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        Objects.requireNonNull(freshness, "freshness must not be null");
        Objects.requireNonNull(failureCategory, "failureCategory must not be null");
        if (rate.isPresent() && source.isEmpty()) {
            throw new IllegalArgumentException("An available FX rate must have a source.");
        }
        if (rate.isPresent() && freshness == DataFreshness.UNAVAILABLE) {
            throw new IllegalArgumentException("An available FX rate cannot be unavailable.");
        }
        if (rate.isEmpty() && freshness != DataFreshness.UNAVAILABLE) {
            throw new IllegalArgumentException("A missing FX rate must be unavailable.");
        }
        rate.ifPresent(value -> {
            if (!fromCurrency.equals(value.fromCurrency()) || !toCurrency.equals(value.toCurrency())) {
                throw new IllegalArgumentException("FX rate currencies must match their quote.");
            }
            if (evaluatedAt.isEmpty() && source.orElseThrow() != MarketDataSource.IDENTITY) {
                throw new IllegalArgumentException("Provider FX rates require evaluatedAt.");
            }
        });
    }
}
