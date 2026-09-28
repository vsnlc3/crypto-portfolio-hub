package com.cryptoportfoliohub.provider;

import java.math.BigDecimal;
import java.util.Objects;

public record NormalizedPerpetualFillDetail(
        String instrumentCode,
        NormalizedPerpetualFillSide side,
        NormalizedPerpetualFillDirection direction,
        String providerDirection,
        BigDecimal quantity,
        BigDecimal price,
        String priceCurrency,
        BigDecimal startPosition,
        BigDecimal closedPnl,
        String closedPnlCurrency) {

    public NormalizedPerpetualFillDetail {
        Objects.requireNonNull(instrumentCode, "instrumentCode must not be null");
        Objects.requireNonNull(side, "side must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(price, "price must not be null");
        if (instrumentCode.isBlank() || quantity.signum() <= 0 || price.signum() <= 0) {
            throw new IllegalArgumentException("A perpetual fill requires an instrument and positive quantity and price.");
        }
    }
}
