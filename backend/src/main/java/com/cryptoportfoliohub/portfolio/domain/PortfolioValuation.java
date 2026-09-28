package com.cryptoportfoliohub.portfolio.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;

public record PortfolioValuation(
        Optional<BigDecimal> netWorthJpy,
        Optional<BigDecimal> holdingsValueJpy,
        Optional<BigDecimal> directionalValueJpy,
        Optional<BigDecimal> stablecoinValueJpy,
        Optional<BigDecimal> marketExposureJpy,
        Optional<BigDecimal> positionValueJpy,
        Optional<BigDecimal> marginJpy,
        Optional<BigDecimal> unrealizedPnlJpy,
        Optional<BigDecimal> exposureRatio,
        DataFreshness freshness) {

    public PortfolioValuation {
        Objects.requireNonNull(netWorthJpy, "netWorthJpy must not be null");
        Objects.requireNonNull(holdingsValueJpy, "holdingsValueJpy must not be null");
        Objects.requireNonNull(directionalValueJpy, "directionalValueJpy must not be null");
        Objects.requireNonNull(stablecoinValueJpy, "stablecoinValueJpy must not be null");
        Objects.requireNonNull(marketExposureJpy, "marketExposureJpy must not be null");
        Objects.requireNonNull(positionValueJpy, "positionValueJpy must not be null");
        Objects.requireNonNull(marginJpy, "marginJpy must not be null");
        Objects.requireNonNull(unrealizedPnlJpy, "unrealizedPnlJpy must not be null");
        Objects.requireNonNull(exposureRatio, "exposureRatio must not be null");
        Objects.requireNonNull(freshness, "freshness must not be null");
    }
}
