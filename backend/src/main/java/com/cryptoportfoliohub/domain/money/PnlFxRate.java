package com.cryptoportfoliohub.domain.money;

import java.util.Objects;

public record PnlFxRate(FxRate value) {

    public PnlFxRate {
        Objects.requireNonNull(value, "value must not be null");
        if (!CurrencyCode.JPY.equals(value.toCurrency())) {
            throw new IllegalArgumentException("PnL FX rate must convert to JPY.");
        }
    }
}
