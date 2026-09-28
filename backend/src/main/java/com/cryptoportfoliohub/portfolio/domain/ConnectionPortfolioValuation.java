package com.cryptoportfoliohub.portfolio.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public record ConnectionPortfolioValuation(
        Optional<BigDecimal> amountJpy,
        ConnectionPortfolioStatus status) {

    public ConnectionPortfolioValuation {
        Objects.requireNonNull(amountJpy, "amountJpy must not be null");
        Objects.requireNonNull(status, "status must not be null");
        boolean complete = status == ConnectionPortfolioStatus.COMPLETE
                || status == ConnectionPortfolioStatus.STALE;
        if (amountJpy.isPresent() != complete) {
            throw new IllegalArgumentException("Only a complete connection valuation has an amount.");
        }
    }
}
