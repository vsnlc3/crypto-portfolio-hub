package com.cryptoportfoliohub.domain.money;

import java.math.BigDecimal;
import java.util.Objects;

public record Price(String assetKey, BigDecimal amount, CurrencyCode currency) {

    public Price {
        Objects.requireNonNull(assetKey, "assetKey must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (assetKey.isBlank()) {
            throw new IllegalArgumentException("assetKey must not be blank.");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Price must be non-negative.");
        }
    }

    public static Price of(String assetKey, String amount, CurrencyCode currency) {
        return new Price(assetKey, new BigDecimal(amount), currency);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Price price)) {
            return false;
        }
        return assetKey.equals(price.assetKey)
                && DecimalEquality.equal(amount, price.amount)
                && currency.equals(price.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetKey, DecimalEquality.hashCode(amount), currency);
    }
}
