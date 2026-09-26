package com.cryptoportfoliohub.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MoneyDomainTests {

    private final PerpetualValuation perpetualValuation = new PerpetualValuation();

    @Test
    void convertsUsdMoneyToJpyWithoutRounding() {
        Money usd = Money.of("12.345678901234567890", CurrencyCode.USD);
        FxRate usdToJpy = FxRate.of("USD", "JPY", "149.123456789012");

        Optional<Money> result = JpyConverter.convert(usd, Optional.of(usdToJpy));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().amount())
                .isEqualByComparingTo(new BigDecimal("1841.030314159270231644316720024680"));
        assertThat(result.orElseThrow().currency()).isEqualTo(CurrencyCode.JPY);
    }

    @Test
    void jpyConversionIsIdentityWithoutAnFxRate() {
        Money jpy = Money.of("1234.56789012", CurrencyCode.JPY);

        assertThat(JpyConverter.convert(jpy, Optional.empty())).containsSame(jpy);
    }

    @Test
    void refusesToAddDifferentCurrencies() {
        Money usd = Money.of("10", CurrencyCode.USD);
        Money jpy = Money.of("10", CurrencyCode.JPY);

        assertThatIllegalArgumentException().isThrownBy(() -> usd.plus(jpy));
    }

    @Test
    void currencyCodeFitsDatabaseCurrencyColumns() {
        assertThat(new CurrencyCode("USDC")).isEqualTo(new CurrencyCode("USDC"));
        assertThatIllegalArgumentException().isThrownBy(() -> new CurrencyCode("TOOLONG99"));
    }

    @Test
    void retainsDatabaseQuantityScaleAsAnExactBigDecimal() {
        AssetQuantity quantity = AssetQuantity.of("BTC", "0.000000000000000001");

        assertThat(quantity.amount()).isEqualByComparingTo(new BigDecimal("0.000000000000000001"));
        assertThat(quantity.amount().scale()).isEqualTo(18);
    }

    @Test
    void computesLongAndShortUnrealizedPnlWithOppositeSigns() {
        AssetQuantity quantity = AssetQuantity.of("BTC", "2");
        Price entry = Price.of("BTC", "30000", CurrencyCode.USD);
        Price mark = Price.of("BTC", "31000", CurrencyCode.USD);

        assertThat(perpetualValuation.linearUnrealizedPnl(PositionSide.LONG, quantity, entry, mark))
                .isEqualTo(Money.of("2000", CurrencyCode.USD));
        assertThat(perpetualValuation.linearUnrealizedPnl(PositionSide.SHORT, quantity, entry, mark))
                .isEqualTo(Money.of("-2000", CurrencyCode.USD));
    }

    @Test
    void calculatesPositionValueAndConvertsUsingPriceFx() {
        AssetQuantity quantity = AssetQuantity.of("BTC", "0.125");
        Price mark = Price.of("BTC", "60000", CurrencyCode.USD);
        PriceFxRate priceFxRate = new PriceFxRate(FxRate.of("USD", "JPY", "150"));

        assertThat(perpetualValuation.positionValue(quantity, mark))
                .isEqualTo(Money.of("7500.000", CurrencyCode.USD));
        assertThat(perpetualValuation.positionValueJpy(quantity, Optional.of(mark), Optional.of(priceFxRate)))
                .contains(Money.of("1125000.000", CurrencyCode.JPY));
    }

    @Test
    void convertsMarginAndPnlWithTheirOwnCurrenciesAndFxRates() {
        Money margin = Money.of("2500", new CurrencyCode("USDC"));
        Money pnl = Money.of("-12.5", new CurrencyCode("EUR"));
        MarginFxRate marginFxRate = new MarginFxRate(FxRate.of("USDC", "JPY", "151.25"));
        PnlFxRate pnlFxRate = new PnlFxRate(FxRate.of("EUR", "JPY", "160.4"));

        assertThat(perpetualValuation.marginJpy(margin, Optional.of(marginFxRate)))
                .contains(Money.of("378125.00", CurrencyCode.JPY));
        assertThat(perpetualValuation.unrealizedPnlJpy(pnl, Optional.of(pnlFxRate)))
                .contains(Money.of("-2005.00", CurrencyCode.JPY));
    }

    @Test
    void keepsUnavailableFxAsAnUnavailableValuation() {
        Money usd = Money.of("100", CurrencyCode.USD);

        assertThat(JpyConverter.convert(usd, Optional.empty())).isEmpty();
    }

    @Test
    void keepsUnavailableMarkPriceAsAnUnavailablePositionValue() {
        AssetQuantity quantity = AssetQuantity.of("BTC", "1");

        assertThat(perpetualValuation.positionValueJpy(quantity, Optional.empty(), Optional.empty())).isEmpty();
    }

    @Test
    void rejectsAnFxRateForTheWrongCurrency() {
        Money usd = Money.of("100", CurrencyCode.USD);
        FxRate eurToJpy = FxRate.of("EUR", "JPY", "160");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> JpyConverter.convert(usd, Optional.of(eurToJpy)));
    }

    @Test
    void roundsOnlyWhenExplicitlyRequestedForDisplay() {
        BigDecimal exactValue = new BigDecimal("1.005");

        BigDecimal displayed = DisplayRounding.round(exactValue, 2, RoundingMode.HALF_UP);

        assertThat(displayed).isEqualByComparingTo(new BigDecimal("1.01"));
        assertThat(exactValue).isEqualByComparingTo(new BigDecimal("1.005"));
    }

    @Test
    void numericValueObjectsCompareByAmountInsteadOfBigDecimalScale() {
        assertThat(Money.of("1.0", CurrencyCode.JPY)).isEqualTo(Money.of("1.00", CurrencyCode.JPY));
        assertThat(AssetQuantity.of("BTC", "1.0")).isEqualTo(AssetQuantity.of("BTC", "1.00"));
        assertThat(Price.of("BTC", "1.0", CurrencyCode.USD))
                .isEqualTo(Price.of("BTC", "1.00", CurrencyCode.USD));
        assertThat(FxRate.of("USD", "JPY", "150.0"))
                .isEqualTo(FxRate.of("USD", "JPY", "150.00"));
    }

    @Test
    void refusesToCalculateValueForMismatchedAssetsOrPriceCurrencies() {
        AssetQuantity bitcoin = AssetQuantity.of("BTC", "1");
        Price solana = Price.of("SOL", "100", CurrencyCode.USD);

        assertThatIllegalArgumentException().isThrownBy(() -> perpetualValuation.positionValue(bitcoin, solana));
        assertThatIllegalArgumentException().isThrownBy(() -> perpetualValuation.linearUnrealizedPnl(
                PositionSide.LONG,
                bitcoin,
                Price.of("BTC", "30000", CurrencyCode.USD),
                Price.of("BTC", "31000", new CurrencyCode("USDC"))));
    }
}
