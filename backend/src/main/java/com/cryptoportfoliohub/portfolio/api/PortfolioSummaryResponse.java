package com.cryptoportfoliohub.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.cryptoportfoliohub.api.DecimalStringSerializer;
import com.cryptoportfoliohub.connection.api.CapabilitySyncResponse;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.portfolio.domain.ConnectionPortfolioStatus;
import tools.jackson.databind.annotation.JsonSerialize;

public record PortfolioSummaryResponse(Summary summary, List<Connection> connections) {

    public PortfolioSummaryResponse {
        connections = List.copyOf(connections);
    }

    public record Summary(
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal netWorthJpy,
            Change24h change24h,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal holdingsValueJpy,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal directionalValueJpy,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal stablecoinValueJpy,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal marketExposureJpy,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal exposureRatio,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal unrealizedPnlJpy,
            PortfolioSummaryStatus status,
            Instant dataAsOfAt,
            Instant lastSuccessfulSyncAt) {
    }

    public record Change24h(
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal amountJpy,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal percentage,
            PortfolioSummaryStatus status,
            Instant baselineSnapshotAt,
            Instant currentSnapshotAt) {
    }

    public record Connection(
            UUID id,
            ConnectionProvider provider,
            String displayName,
            @JsonSerialize(using = DecimalStringSerializer.class)
            BigDecimal netWorthJpy,
            ConnectionPortfolioStatus dataStatus,
            Instant lastAttemptAt,
            Instant lastSuccessfulSyncAt,
            List<CapabilitySyncResponse> capabilitySync) {

        public Connection {
            capabilitySync = List.copyOf(capabilitySync);
        }
    }
}
