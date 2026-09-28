package com.cryptoportfoliohub.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.persistence.entity.AssetCategory;

public final class PortfolioCalculator {

    public PortfolioValuation calculate(
            List<PortfolioBalanceValue> balances,
            List<PortfolioPositionValue> positions,
            Optional<BigDecimal> otherNetWorthJpy,
            boolean hasConnections,
            boolean requiredCurrentStateAvailable,
            boolean stale) {
        if (!hasConnections || !requiredCurrentStateAvailable) {
            return unavailable();
        }

        Optional<BigDecimal> allHoldings = sumBalances(balances, ignored -> true);
        Optional<BigDecimal> directional = sumBalances(
                balances, value -> value.category() == AssetCategory.CRYPTO);
        Optional<BigDecimal> stablecoin = sumBalances(
                balances, value -> value.category() == AssetCategory.STABLECOIN);
        Optional<BigDecimal> positionValue = sumPositions(positions, PortfolioPositionValue::positionValueJpy);
        Optional<BigDecimal> margin = sumPositions(positions, PortfolioPositionValue::marginJpy);
        Optional<BigDecimal> unrealizedPnl = sumPositions(positions, PortfolioPositionValue::unrealizedPnlJpy);
        Optional<BigDecimal> exposure = directional.flatMap(direction -> positionValue
                .map(direction::add));
        Optional<BigDecimal> netWorth = allHoldings.flatMap(holdings -> otherNetWorthJpy
                .map(holdings::add));
        Optional<BigDecimal> exposureRatio = netWorth.filter(value -> value.signum() != 0)
                .flatMap(value -> exposure.map(exposureValue -> exposureValue.divide(value, 8, RoundingMode.HALF_UP)));

        boolean complete = netWorth.isPresent()
                && allHoldings.isPresent()
                && directional.isPresent()
                && stablecoin.isPresent()
                && exposure.isPresent()
                && positionValue.isPresent()
                && margin.isPresent()
                && unrealizedPnl.isPresent();
        DataFreshness freshness = !complete
                ? DataFreshness.UNAVAILABLE
                : stale || balances.stream().anyMatch(PortfolioBalanceValue::stale)
                        || positions.stream().anyMatch(PortfolioPositionValue::stale)
                        ? DataFreshness.STALE : DataFreshness.FRESH;
        return new PortfolioValuation(netWorth, allHoldings, directional, stablecoin, exposure,
                positionValue, margin, unrealizedPnl, exposureRatio, freshness);
    }

    private static Optional<BigDecimal> sumBalances(
            List<PortfolioBalanceValue> balances, Predicate<PortfolioBalanceValue> included) {
        List<PortfolioBalanceValue> selected = balances.stream().filter(included).toList();
        if (selected.stream().anyMatch(value -> value.valueJpy().isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(selected.stream()
                .map(value -> value.valueJpy().orElseThrow())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static Optional<BigDecimal> sumPositions(
            List<PortfolioPositionValue> positions,
            java.util.function.Function<PortfolioPositionValue, Optional<BigDecimal>> amount) {
        if (positions.stream().anyMatch(position -> amount.apply(position).isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(positions.stream()
                .map(position -> amount.apply(position).orElseThrow())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static PortfolioValuation unavailable() {
        Optional<BigDecimal> none = Optional.empty();
        return new PortfolioValuation(none, none, none, none, none, none, none, none, none,
                DataFreshness.UNAVAILABLE);
    }
}
