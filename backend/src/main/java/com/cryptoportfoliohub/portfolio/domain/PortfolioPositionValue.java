package com.cryptoportfoliohub.portfolio.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public record PortfolioPositionValue(
        Optional<BigDecimal> positionValueJpy,
        Optional<BigDecimal> marginJpy,
        Optional<BigDecimal> unrealizedPnlJpy,
        boolean stale,
        boolean snapshotStale) {

    public PortfolioPositionValue(
            Optional<BigDecimal> positionValueJpy,
            Optional<BigDecimal> marginJpy,
            Optional<BigDecimal> unrealizedPnlJpy,
            boolean stale) {
        this(positionValueJpy, marginJpy, unrealizedPnlJpy, stale, stale);
    }

    public PortfolioPositionValue {
        Objects.requireNonNull(positionValueJpy, "positionValueJpy must not be null");
        Objects.requireNonNull(marginJpy, "marginJpy must not be null");
        Objects.requireNonNull(unrealizedPnlJpy, "unrealizedPnlJpy must not be null");
    }
}
