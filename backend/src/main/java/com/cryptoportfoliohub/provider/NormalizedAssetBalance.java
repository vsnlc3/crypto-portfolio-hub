package com.cryptoportfoliohub.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record NormalizedAssetBalance(
        String assetKey,
        String symbol,
        String assetName,
        NormalizedAssetCategory category,
        String network,
        String assetRef,
        BigDecimal totalQuantity,
        BigDecimal availableQuantity,
        BigDecimal lockedQuantity,
        Instant fetchedAt) {

    public NormalizedAssetBalance(
            String assetKey,
            String symbol,
            String assetName,
            NormalizedAssetCategory category,
            String network,
            String assetRef,
            BigDecimal totalQuantity,
            Instant fetchedAt) {
        this(assetKey, symbol, assetName, category, network, assetRef, totalQuantity, null, null, fetchedAt);
    }

    public NormalizedAssetBalance {
        Objects.requireNonNull(assetKey, "assetKey must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(network, "network must not be null");
        Objects.requireNonNull(totalQuantity, "totalQuantity must not be null");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
        if (assetKey.isBlank() || network.isBlank() || totalQuantity.signum() < 0) {
            throw new IllegalArgumentException("A normalized balance must have an identity and non-negative quantity.");
        }
        if ((availableQuantity != null && availableQuantity.signum() < 0)
                || (lockedQuantity != null && lockedQuantity.signum() < 0)) {
            throw new IllegalArgumentException("Available and locked quantities must be non-negative when present.");
        }
    }
}
