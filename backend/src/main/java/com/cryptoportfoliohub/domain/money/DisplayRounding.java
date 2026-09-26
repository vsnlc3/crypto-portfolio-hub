package com.cryptoportfoliohub.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class DisplayRounding {

    private DisplayRounding() {
    }

    public static BigDecimal round(BigDecimal value, int scale, RoundingMode roundingMode) {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(roundingMode, "roundingMode must not be null");
        if (scale < 0) {
            throw new IllegalArgumentException("Display scale must not be negative.");
        }
        return value.setScale(scale, roundingMode);
    }
}
