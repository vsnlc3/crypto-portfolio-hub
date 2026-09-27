package com.cryptoportfoliohub.marketdata.coingecko;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record CoinGeckoPriceObservation(BigDecimal amount, Instant evaluatedAt) {

    public CoinGeckoPriceObservation {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("A provider price must not be negative.");
        }
    }
}
