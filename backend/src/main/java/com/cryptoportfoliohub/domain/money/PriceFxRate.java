package com.cryptoportfoliohub.domain.money;

import java.util.Objects;

public record PriceFxRate(FxRate value) {

    public PriceFxRate {
        Objects.requireNonNull(value, "value must not be null");
        if (!CurrencyCode.JPY.equals(value.toCurrency())) {
            throw new IllegalArgumentException("Price FX rate must convert to JPY.");
        }
    }
}
