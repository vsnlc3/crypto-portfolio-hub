package com.cryptoportfoliohub.activity.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.cryptoportfoliohub.api.DecimalStringSerializer;
import com.cryptoportfoliohub.persistence.entity.ActivityDirection;
import com.cryptoportfoliohub.persistence.entity.ActivityType;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.PerpetualFillDirection;
import com.cryptoportfoliohub.persistence.entity.PerpetualFillSide;
import com.cryptoportfoliohub.persistence.entity.ValuationBasis;
import com.cryptoportfoliohub.persistence.entity.ValuationStatus;
import tools.jackson.databind.annotation.JsonSerialize;

public record ActivitiesResponse(
        Summary summary,
        List<ActivityItem> activities,
        String nextCursor,
        boolean hasMore) {

    public ActivitiesResponse {
        activities = List.copyOf(activities);
    }

    public record Summary(
            ActivityDataStatus status,
            int connectionCount,
            int syncedConnectionCount,
            Instant lastSuccessAt) {
    }

    public record ActivityItem(
            UUID id,
            UUID connectionId,
            ConnectionProvider provider,
            String connectionDisplayName,
            String providerEventId,
            ActivityType eventType,
            String originalEventType,
            String status,
            Instant occurredAt,
            Instant importedAt,
            ActivityDataStatus dataStatus,
            Instant lastSuccessAt,
            List<ActivityLegItem> legs,
            PerpetualFillItem perpetualFill) {

        public ActivityItem {
            legs = List.copyOf(legs);
        }
    }

    public record ActivityLegItem(
            int legIndex,
            ActivityDirection direction,
            String assetKey,
            String symbol,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal quantity,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal originalAmount,
            String originalCurrency,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal jpyValue,
            ValuationStatus valuationStatus,
            ValuationBasis valuationBasis,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal priceUsed,
            String priceCurrency,
            String priceSource,
            Instant priceEvaluatedAt,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal fxRateToJpy,
            String fxSource,
            Instant fxEvaluatedAt) {
    }

    public record PerpetualFillItem(
            String instrumentCode,
            PerpetualFillSide side,
            PerpetualFillDirection direction,
            String providerDirection,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal quantity,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal price,
            String priceCurrency,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal startPosition,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal closedPnl,
            String closedPnlCurrency) {
    }
}
