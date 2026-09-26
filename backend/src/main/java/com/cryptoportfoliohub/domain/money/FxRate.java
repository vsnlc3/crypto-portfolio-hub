package com.cryptoportfoliohub.domain.money;

import java.math.BigDecimal;
import java.util.Objects;

public record FxRate(CurrencyCode fromCurrency, CurrencyCode toCurrency, BigDecimal rate) {

    public FxRate {
        Objects.requireNonNull(fromCurrency, "fromCurrency must not be null");
        Objects.requireNonNull(toCurrency, "toCurrency must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("FX rate must be positive.");
        }
        if (fromCurrency.equals(toCurrency) && rate.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("An identity FX rate must equal one.");
        }
    }

    public static FxRate of(String fromCurrency, String toCurrency, String rate) {
        return new FxRate(new CurrencyCode(fromCurrency), new CurrencyCode(toCurrency), new BigDecimal(rate));
    }

    public static FxRate identity(CurrencyCode currency) {
        return new FxRate(currency, currency, BigDecimal.ONE);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FxRate fxRate)) {
            return false;
        }
        return fromCurrency.equals(fxRate.fromCurrency)
                && toCurrency.equals(fxRate.toCurrency)
                && DecimalEquality.equal(rate, fxRate.rate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromCurrency, toCurrency, DecimalEquality.hashCode(rate));
    }
}
