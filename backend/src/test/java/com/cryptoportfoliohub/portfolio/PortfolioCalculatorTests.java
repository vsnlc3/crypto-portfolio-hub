package com.cryptoportfoliohub.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.persistence.entity.AssetCategory;
import com.cryptoportfoliohub.portfolio.domain.PortfolioBalanceValue;
import com.cryptoportfoliohub.portfolio.domain.PortfolioCalculator;
import com.cryptoportfoliohub.portfolio.domain.PortfolioPositionValue;
import static org.assertj.core.api.Assertions.assertThat;

class PortfolioCalculatorTests {

    private final PortfolioCalculator calculator = new PortfolioCalculator();

    @Test
    void calculatesJpyTotalsAndDoesNotAddPerpetualPositionValueToNetWorth() {
        var result = calculator.calculate(
                List.of(
                        balance(AssetCategory.CRYPTO, "15000", false),
                        balance(AssetCategory.STABLECOIN, "30000", false),
                        balance(AssetCategory.FIAT, "5000", false)),
                List.of(position("9000", "2500", "-500", false)),
                Optional.of(new BigDecimal("10000")), true, true, false);

        assertThat(result.holdingsValueJpy()).contains(new BigDecimal("50000"));
        assertThat(result.directionalValueJpy()).contains(new BigDecimal("15000"));
        assertThat(result.stablecoinValueJpy()).contains(new BigDecimal("30000"));
        assertThat(result.positionValueJpy()).contains(new BigDecimal("9000"));
        assertThat(result.marginJpy()).contains(new BigDecimal("2500"));
        assertThat(result.unrealizedPnlJpy()).contains(new BigDecimal("-500"));
        assertThat(result.marketExposureJpy()).contains(new BigDecimal("24000"));
        assertThat(result.netWorthJpy()).contains(new BigDecimal("60000"));
        assertThat(result.exposureRatio()).contains(new BigDecimal("0.40000000"));
        assertThat(result.freshness()).isEqualTo(DataFreshness.FRESH);
    }

    @Test
    void calculatesStablecoinOnlyPortfolioWithZeroDirectionalExposure() {
        var result = calculator.calculate(
                List.of(balance(AssetCategory.STABLECOIN, "12500", false)),
                List.of(), Optional.of(BigDecimal.ZERO), true, true, false);

        assertThat(result.netWorthJpy()).contains(new BigDecimal("12500"));
        assertThat(result.holdingsValueJpy()).contains(new BigDecimal("12500"));
        assertThat(result.directionalValueJpy()).contains(BigDecimal.ZERO);
        assertThat(result.stablecoinValueJpy()).contains(new BigDecimal("12500"));
        assertThat(result.marketExposureJpy()).contains(BigDecimal.ZERO);
        assertThat(result.exposureRatio()).contains(BigDecimal.ZERO.setScale(8));
    }

    @Test
    void addsLongAndShortPositionValuesAsGrossExposure() {
        var result = calculator.calculate(
                List.of(balance(AssetCategory.CRYPTO, "1000", false)),
                List.of(position("800", "100", "20", false), position("700", "100", "-30", false)),
                Optional.of(BigDecimal.ZERO), true, true, false);

        assertThat(result.positionValueJpy()).contains(new BigDecimal("1500"));
        assertThat(result.marketExposureJpy()).contains(new BigDecimal("2500"));
        assertThat(result.unrealizedPnlJpy()).contains(new BigDecimal("-10"));
    }

    @Test
    void keepsUnknownPriceSeparateFromKnownZeroAndDoesNotReportPartialTotals() {
        var result = calculator.calculate(
                List.of(
                        balance(AssetCategory.CRYPTO, "0", false),
                        new PortfolioBalanceValue(AssetCategory.CRYPTO, Optional.empty(), false)),
                List.of(), Optional.of(BigDecimal.ZERO), true, true, false);

        assertThat(result.netWorthJpy()).isEmpty();
        assertThat(result.directionalValueJpy()).isEmpty();
        assertThat(result.positionValueJpy()).contains(BigDecimal.ZERO);
        assertThat(result.freshness()).isEqualTo(DataFreshness.UNAVAILABLE);
    }

    @Test
    void marksStaleInputsAndDoesNotTreatNoConnectionsAsAZeroPortfolio() {
        var stale = calculator.calculate(
                List.of(balance(AssetCategory.CRYPTO, "100", true)), List.of(), Optional.of(BigDecimal.ZERO),
                true, true, false);
        var empty = calculator.calculate(List.of(), List.of(), Optional.of(BigDecimal.ZERO), false, false, false);

        assertThat(stale.freshness()).isEqualTo(DataFreshness.STALE);
        assertThat(empty.netWorthJpy()).isEmpty();
        assertThat(empty.freshness()).isEqualTo(DataFreshness.UNAVAILABLE);
    }

    @Test
    void snapshotFreshnessOnlyIncludesTheSixPersistedMetricsAndTheirInputs() {
        var marginUnavailable = new PortfolioPositionValue(
                Optional.of(new BigDecimal("9000")),
                Optional.empty(),
                Optional.of(new BigDecimal("-500")),
                true,
                false);
        var result = calculator.calculate(
                List.of(balance(AssetCategory.CRYPTO, "15000", false)),
                List.of(marginUnavailable),
                Optional.of(new BigDecimal("10000")),
                true,
                true,
                false,
                Optional.of(Instant.parse("2026-09-28T01:00:00Z")),
                false);

        assertThat(result.freshness()).isEqualTo(DataFreshness.UNAVAILABLE);
        assertThat(result.snapshotFreshness()).isEqualTo(DataFreshness.FRESH);
        assertThat(result.hasSnapshotValues()).isTrue();
    }

    private PortfolioBalanceValue balance(AssetCategory category, String value, boolean stale) {
        return new PortfolioBalanceValue(category, Optional.of(new BigDecimal(value)), stale);
    }

    private PortfolioPositionValue position(String value, String margin, String pnl, boolean stale) {
        return new PortfolioPositionValue(Optional.of(new BigDecimal(value)), Optional.of(new BigDecimal(margin)),
                Optional.of(new BigDecimal(pnl)), stale);
    }
}
