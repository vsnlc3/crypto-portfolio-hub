package com.cryptoportfoliohub.marketdata.coingecko;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CoinGeckoPriceObservation(
        BigDecimal amount,
        Optional<BigDecimal> change24hPercentage,
        Instant evaluatedAt) {

    public CoinGeckoPriceObservation {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(change24hPercentage, "change24hPercentage must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("A provider price must not be negative.");
        }
    }

    public CoinGeckoPriceObservation(BigDecimal amount, Instant evaluatedAt) {
        this(amount, Optional.empty(), evaluatedAt);
    }
}
