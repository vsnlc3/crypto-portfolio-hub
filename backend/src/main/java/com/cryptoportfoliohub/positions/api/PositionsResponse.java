package com.cryptoportfoliohub.positions.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.cryptoportfoliohub.domain.money.PositionSide;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;

public record PositionsResponse(Summary summary, List<Position> positions) {

    public PositionsResponse {
        positions = List.copyOf(positions);
    }

    public record Summary(
            BigDecimal positionValueJpy,
            BigDecimal marginJpy,
            BigDecimal unrealizedPnlJpy,
            PositionDataStatus status,
            int connectionCount,
            int syncedConnectionCount) {
    }

    public record Position(
            String positionKey,
            String instrumentCode,
            PositionSide side,
            BigDecimal leverage,
            BigDecimal quantity,
            BigDecimal entryPrice,
            BigDecimal markPrice,
            BigDecimal liquidationPrice,
            String priceCurrency,
            BigDecimal positionValueJpy,
            BigDecimal marginAmount,
            String marginCurrency,
            BigDecimal marginJpy,
            BigDecimal unrealizedPnl,
            String pnlCurrency,
            BigDecimal unrealizedPnlJpy,
            FxMetadata priceFx,
            FxMetadata marginFx,
            FxMetadata pnlFx,
            PositionDataStatus status,
            UUID connectionId,
            ConnectionProvider provider,
            String connectionDisplayName,
            Instant fetchedAt,
            Instant lastSuccessAt) {
    }

    public record FxMetadata(
            String currency,
            BigDecimal rateToJpy,
            String source,
            Instant evaluatedAt,
            PositionDataStatus status) {
    }
}
