package com.cryptoportfoliohub.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record NormalizedProviderAccountState(
        String accountScope,
        String accountMode,
        String providerAbstractionMode,
        String accountCurrency,
        BigDecimal cashBalance,
        BigDecimal collateralBalance,
        BigDecimal accountEquity,
        BigDecimal unrealizedPnl,
        Boolean equityIncludesUnrealizedPnl,
        Instant fetchedAt) {

    public NormalizedProviderAccountState {
        Objects.requireNonNull(accountScope, "accountScope must not be null");
        Objects.requireNonNull(accountMode, "accountMode must not be null");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
        if (accountScope.isBlank()) {
            throw new IllegalArgumentException("accountScope must not be blank.");
        }
    }
}
