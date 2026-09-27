package com.cryptoportfoliohub.marketdata.exchangerate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record FxObservation(BigDecimal rate, Instant evaluatedAt) {

    public FxObservation {
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("A provider FX rate must be positive.");
        }
    }
}
