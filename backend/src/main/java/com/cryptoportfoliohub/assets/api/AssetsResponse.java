package com.cryptoportfoliohub.assets.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.cryptoportfoliohub.persistence.entity.AssetCategory;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;

public record AssetsResponse(Summary summary, List<Asset> assets) {

    public AssetsResponse {
        assets = List.copyOf(assets);
    }

    public record Summary(
            BigDecimal spotHoldingsValueJpy,
            BigDecimal directionalAssetsValueJpy,
            BigDecimal stablecoinsValueJpy,
            AssetDataStatus status,
            int connectionCount,
            int syncedConnectionCount,
            Instant dataAsOfAt) {
    }

    public record Asset(
            String assetId,
            String assetKey,
            String symbol,
            String name,
            AssetCategory category,
            String network,
            BigDecimal totalQuantity,
            BigDecimal valueJpy,
            AssetDataStatus status,
            Price price,
            PriceChange change24h,
            Valuation valuation,
            List<ConnectionHolding> connections) {

        public Asset {
            connections = List.copyOf(connections);
        }
    }

    public record Price(
            BigDecimal amount,
            String currency,
            String source,
            Instant evaluatedAt,
            AssetDataStatus status,
            String failureCategory) {
    }

    public record PriceChange(
            BigDecimal value,
            String unit,
            String comparisonPeriod,
            String source,
            Instant evaluatedAt,
            AssetDataStatus status,
            String failureCategory) {
    }

    public record Valuation(String currency, String fxSource, Instant fxEvaluatedAt) {
    }

    public record ConnectionHolding(
            UUID connectionId,
            ConnectionProvider provider,
            String displayName,
            BigDecimal quantity,
            BigDecimal valueJpy,
            AssetDataStatus status,
            Instant balanceFetchedAt,
            Instant lastSuccessAt) {
    }
}
