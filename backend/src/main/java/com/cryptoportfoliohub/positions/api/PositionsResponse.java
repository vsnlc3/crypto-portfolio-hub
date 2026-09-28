package com.cryptoportfoliohub.positions.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.cryptoportfoliohub.api.DecimalStringSerializer;
import com.cryptoportfoliohub.domain.money.PositionSide;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import tools.jackson.databind.annotation.JsonSerialize;

public record PositionsResponse(Summary summary, List<Position> positions) {

    public PositionsResponse {
        positions = List.copyOf(positions);
    }

    public record Summary(
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal positionValueJpy,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal marginJpy,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal unrealizedPnlJpy,
            PositionDataStatus status,
            int connectionCount,
            int syncedConnectionCount) {
    }

    public record Position(
            String positionKey,
            String instrumentCode,
            PositionSide side,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal leverage,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal quantity,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal entryPrice,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal markPrice,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal liquidationPrice,
            String priceCurrency,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal positionValueJpy,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal marginAmount,
            String marginCurrency,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal marginJpy,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal unrealizedPnl,
            String pnlCurrency,
            @JsonSerialize(using = DecimalStringSerializer.class)
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
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal rateToJpy,
            String source,
            Instant evaluatedAt,
            PositionDataStatus status) {
    }
}
