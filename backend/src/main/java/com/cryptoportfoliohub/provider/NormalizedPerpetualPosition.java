package com.cryptoportfoliohub.provider;

import com.cryptoportfoliohub.domain.money.PositionSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record NormalizedPerpetualPosition(
        String positionKey,
        String instrumentCode,
        PositionSide side,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal markPrice,
        BigDecimal liquidationPrice,
        String priceCurrency,
        BigDecimal leverage,
        BigDecimal marginAmount,
        String marginCurrency,
        BigDecimal unrealizedPnl,
        String pnlCurrency,
        Instant fetchedAt) {

    public NormalizedPerpetualPosition {
        Objects.requireNonNull(positionKey, "positionKey must not be null");
        Objects.requireNonNull(instrumentCode, "instrumentCode must not be null");
        Objects.requireNonNull(side, "side must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
        if (positionKey.isBlank() || instrumentCode.isBlank() || quantity.signum() <= 0) {
            throw new IllegalArgumentException("A normalized position requires an identity and positive quantity.");
        }
    }
}
