package com.cryptoportfoliohub.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.cryptoportfoliohub.connection.api.CapabilitySyncResponse;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.portfolio.domain.ConnectionPortfolioStatus;

public record PortfolioSummaryResponse(Summary summary, List<Connection> connections) {

    public PortfolioSummaryResponse {
        connections = List.copyOf(connections);
    }

    public record Summary(
            BigDecimal netWorthJpy,
            Change24h change24h,
            BigDecimal holdingsValueJpy,
            BigDecimal directionalValueJpy,
            BigDecimal stablecoinValueJpy,
            BigDecimal marketExposureJpy,
            BigDecimal exposureRatio,
            BigDecimal unrealizedPnlJpy,
            PortfolioSummaryStatus status,
            Instant dataAsOfAt,
            Instant lastSuccessfulSyncAt) {
    }

    public record Change24h(
            BigDecimal amountJpy,
            BigDecimal percentage,
            PortfolioSummaryStatus status,
            Instant comparedAt) {
    }

    public record Connection(
            UUID id,
            ConnectionProvider provider,
            String displayName,
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
