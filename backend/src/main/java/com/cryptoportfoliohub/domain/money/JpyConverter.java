package com.cryptoportfoliohub.domain.money;

import java.util.Objects;
import java.util.Optional;

public final class JpyConverter {

    private JpyConverter() {
    }

    public static Optional<Money> convert(Money amount, Optional<FxRate> fxRate) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(fxRate, "fxRate must not be null");

        if (CurrencyCode.JPY.equals(amount.currency())) {
            return Optional.of(amount);
        }
        return fxRate.map(rate -> {
            if (!amount.currency().equals(rate.fromCurrency())
                    || !CurrencyCode.JPY.equals(rate.toCurrency())) {
                throw new IllegalArgumentException("FX rate must convert the amount currency to JPY.");
            }
            return new Money(amount.amount().multiply(rate.rate()), CurrencyCode.JPY);
        });
    }
}
