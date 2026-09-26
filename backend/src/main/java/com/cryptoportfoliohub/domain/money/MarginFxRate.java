package com.cryptoportfoliohub.domain.money;

import java.util.Objects;

public record MarginFxRate(FxRate value) {

    public MarginFxRate {
        Objects.requireNonNull(value, "value must not be null");
        if (!CurrencyCode.JPY.equals(value.toCurrency())) {
            throw new IllegalArgumentException("Margin FX rate must convert to JPY.");
        }
    }
}
