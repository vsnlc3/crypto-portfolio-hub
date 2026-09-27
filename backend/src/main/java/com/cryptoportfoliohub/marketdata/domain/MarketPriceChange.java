package com.cryptoportfoliohub.marketdata.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record MarketPriceChange(BigDecimal value, Unit unit, ComparisonPeriod comparisonPeriod) {

    public MarketPriceChange {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(comparisonPeriod, "comparisonPeriod must not be null");
    }

    public enum Unit {
        PERCENTAGE
    }

    public enum ComparisonPeriod {
        H24
    }
}
