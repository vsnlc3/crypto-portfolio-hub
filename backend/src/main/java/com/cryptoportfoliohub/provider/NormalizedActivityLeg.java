package com.cryptoportfoliohub.provider;

import java.math.BigDecimal;
import java.util.Objects;

public record NormalizedActivityLeg(
        int legIndex,
        NormalizedDirection direction,
        String assetKey,
        String symbol,
        BigDecimal quantity,
        BigDecimal originalAmount,
        String originalCurrency) {

    public NormalizedActivityLeg {
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(assetKey, "assetKey must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        if (legIndex < 0 || assetKey.isBlank() || quantity.signum() <= 0) {
            throw new IllegalArgumentException("A normalized activity leg must have a positive quantity and identity.");
        }
    }
}
