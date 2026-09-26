package com.cryptoportfoliohub.domain.money;

import java.math.BigDecimal;
import java.util.Objects;

public record AssetQuantity(String assetKey, BigDecimal amount) {

    public AssetQuantity {
        Objects.requireNonNull(assetKey, "assetKey must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (assetKey.isBlank()) {
            throw new IllegalArgumentException("assetKey must not be blank.");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Asset quantity must be non-negative.");
        }
    }

    public static AssetQuantity of(String assetKey, String amount) {
        return new AssetQuantity(assetKey, new BigDecimal(amount));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AssetQuantity quantity)) {
            return false;
        }
        return assetKey.equals(quantity.assetKey) && DecimalEquality.equal(amount, quantity.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetKey, DecimalEquality.hashCode(amount));
    }
}
