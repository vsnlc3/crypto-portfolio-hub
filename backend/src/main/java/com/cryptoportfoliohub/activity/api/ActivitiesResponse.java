package com.cryptoportfoliohub.activity.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.cryptoportfoliohub.persistence.entity.ActivityDirection;
import com.cryptoportfoliohub.persistence.entity.ActivityType;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.PerpetualFillDirection;
import com.cryptoportfoliohub.persistence.entity.PerpetualFillSide;
import com.cryptoportfoliohub.persistence.entity.ValuationBasis;
import com.cryptoportfoliohub.persistence.entity.ValuationStatus;

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
            BigDecimal quantity,
            BigDecimal originalAmount,
            String originalCurrency,
            BigDecimal jpyValue,
            ValuationStatus valuationStatus,
            ValuationBasis valuationBasis,
            BigDecimal priceUsed,
            String priceCurrency,
            String priceSource,
            Instant priceEvaluatedAt,
            BigDecimal fxRateToJpy,
            String fxSource,
            Instant fxEvaluatedAt) {
    }

    public record PerpetualFillItem(
            String instrumentCode,
            PerpetualFillSide side,
            PerpetualFillDirection direction,
            String providerDirection,
            BigDecimal quantity,
            BigDecimal price,
            String priceCurrency,
            BigDecimal startPosition,
            BigDecimal closedPnl,
            String closedPnlCurrency) {
    }
}
