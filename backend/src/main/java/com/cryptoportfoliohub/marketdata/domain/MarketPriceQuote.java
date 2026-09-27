package com.cryptoportfoliohub.marketdata.domain;

import com.cryptoportfoliohub.domain.money.Price;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record MarketPriceQuote(
        String assetKey,
        Optional<Price> price,
        Optional<MarketDataSource> source,
        Optional<Instant> evaluatedAt,
        DataFreshness freshness,
        Optional<ProviderErrorCategory> failureCategory) {

    public MarketPriceQuote {
        Objects.requireNonNull(assetKey, "assetKey must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        Objects.requireNonNull(freshness, "freshness must not be null");
        Objects.requireNonNull(failureCategory, "failureCategory must not be null");
        if (price.isPresent() != evaluatedAt.isPresent()) {
            throw new IllegalArgumentException("A market price and its evaluatedAt must be present together.");
        }
        if (price.isPresent() && (source.isEmpty() || freshness == DataFreshness.UNAVAILABLE)) {
            throw new IllegalArgumentException("An available market price must have a source and freshness.");
        }
        if (price.isEmpty() && freshness != DataFreshness.UNAVAILABLE) {
            throw new IllegalArgumentException("A missing market price must be unavailable.");
        }
        price.ifPresent(value -> {
            if (!assetKey.equals(value.assetKey())) {
                throw new IllegalArgumentException("Market price assetKey must match its quote.");
            }
        });
    }
}
