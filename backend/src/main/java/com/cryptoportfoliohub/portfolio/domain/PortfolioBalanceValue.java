package com.cryptoportfoliohub.portfolio.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import com.cryptoportfoliohub.persistence.entity.AssetCategory;

public record PortfolioBalanceValue(AssetCategory category, Optional<BigDecimal> valueJpy, boolean stale) {

    public PortfolioBalanceValue {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(valueJpy, "valueJpy must not be null");
    }
}
