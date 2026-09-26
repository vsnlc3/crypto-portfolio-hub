package com.cryptoportfoliohub.domain.money;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public final class PerpetualValuation {

    public Money positionValue(AssetQuantity quantity, Price markPrice) {
        requireSameAsset(quantity, markPrice);
        return new Money(quantity.amount().multiply(markPrice.amount()), markPrice.currency());
    }

    public Money linearUnrealizedPnl(
            PositionSide side,
            AssetQuantity quantity,
            Price entryPrice,
            Price markPrice) {
        Objects.requireNonNull(side, "side must not be null");
        requireSameAsset(quantity, entryPrice);
        requireSameAsset(quantity, markPrice);
        if (!entryPrice.currency().equals(markPrice.currency())) {
            throw new IllegalArgumentException("Entry and mark prices must use the same currency.");
        }

        BigDecimal perUnitPnl = side == PositionSide.LONG
                ? markPrice.amount().subtract(entryPrice.amount())
                : entryPrice.amount().subtract(markPrice.amount());
        return new Money(quantity.amount().multiply(perUnitPnl), markPrice.currency());
    }

    public Optional<Money> positionValueJpy(
            AssetQuantity quantity,
            Optional<Price> markPrice,
            Optional<PriceFxRate> priceFxRate) {
        Objects.requireNonNull(markPrice, "markPrice must not be null");
        return markPrice.flatMap(price -> JpyConverter.convert(
                positionValue(quantity, price), priceFxRate.map(PriceFxRate::value)));
    }

    public Optional<Money> marginJpy(Money margin, Optional<MarginFxRate> marginFxRate) {
        return JpyConverter.convert(margin, marginFxRate.map(MarginFxRate::value));
    }

    public Optional<Money> unrealizedPnlJpy(Money unrealizedPnl, Optional<PnlFxRate> pnlFxRate) {
        return JpyConverter.convert(unrealizedPnl, pnlFxRate.map(PnlFxRate::value));
    }

    private void requireSameAsset(AssetQuantity quantity, Price price) {
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(price, "price must not be null");
        if (!quantity.assetKey().equals(price.assetKey())) {
            throw new IllegalArgumentException("Quantity and price must refer to the same asset.");
        }
    }
}
