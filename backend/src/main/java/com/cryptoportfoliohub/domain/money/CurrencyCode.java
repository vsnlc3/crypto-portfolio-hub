package com.cryptoportfoliohub.domain.money;

import java.util.Objects;

public record CurrencyCode(String value) {

    public static final CurrencyCode JPY = new CurrencyCode("JPY");
    public static final CurrencyCode USD = new CurrencyCode("USD");

    public CurrencyCode {
        Objects.requireNonNull(value, "value must not be null");
        if (!value.matches("[A-Z][A-Z0-9]{0,7}")) {
            throw new IllegalArgumentException("Currency code must be 1 to 8 uppercase letters or digits.");
        }
    }
}
