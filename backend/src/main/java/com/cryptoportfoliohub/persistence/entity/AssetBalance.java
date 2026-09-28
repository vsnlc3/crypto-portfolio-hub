package com.cryptoportfoliohub.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "asset_balances", uniqueConstraints =
        @UniqueConstraint(name = "uq_asset_balances_connection_asset",
                columnNames = {"connection_id", "user_id", "asset_key"}))
public class AssetBalance extends UpdatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "connection_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
    })
    private ConnectionEntity connection;

    @Column(name = "asset_key", nullable = false, length = 191)
    private String assetKey;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Column(name = "asset_name", length = 100)
    private String assetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_category", nullable = false, length = 20)
    private AssetCategory assetCategory;

    @Column(name = "network", length = 50)
    private String network;

    @Column(name = "asset_ref", length = 255)
    private String assetRef;

    @Column(name = "total_quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal totalQuantity;

    @Column(name = "available_quantity", precision = 38, scale = 18)
    private BigDecimal availableQuantity;

    @Column(name = "locked_quantity", precision = 38, scale = 18)
    private BigDecimal lockedQuantity;

    @Column(name = "unit_price", precision = 38, scale = 18)
    private BigDecimal unitPrice;

    @Column(name = "price_currency", length = 8)
    private String priceCurrency;

    @Column(name = "price_source", length = 50)
    private String priceSource;

    @Column(name = "price_evaluated_at")
    private Instant priceEvaluatedAt;

    @Column(name = "fx_rate_to_jpy", precision = 24, scale = 12)
    private BigDecimal fxRateToJpy;

    @Column(name = "fx_source", length = 50)
    private String fxSource;

    @Column(name = "fx_evaluated_at")
    private Instant fxEvaluatedAt;

    @Column(name = "jpy_value", precision = 38, scale = 8)
    private BigDecimal jpyValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "valuation_status", nullable = false, length = 20)
    private ValuationStatus valuationStatus;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "last_success_sync_run_id", nullable = false)
    private UUID lastSuccessSyncRunId;

    protected AssetBalance() {
    }

    public AssetBalance(
            ConnectionEntity connection,
            String assetKey,
            String symbol,
            String assetName,
            AssetCategory assetCategory,
            String network,
            String assetRef,
            BigDecimal totalQuantity,
            Instant fetchedAt,
            UUID lastSuccessSyncRunId) {
        this(connection, assetKey, symbol, assetName, assetCategory, network, assetRef,
                totalQuantity, null, null, fetchedAt, lastSuccessSyncRunId);
    }

    public AssetBalance(
            ConnectionEntity connection,
            String assetKey,
            String symbol,
            String assetName,
            AssetCategory assetCategory,
            String network,
            String assetRef,
            BigDecimal totalQuantity,
            BigDecimal availableQuantity,
            BigDecimal lockedQuantity,
            Instant fetchedAt,
            UUID lastSuccessSyncRunId) {
        this.connection = connection;
        this.assetKey = assetKey;
        this.symbol = symbol;
        this.assetName = assetName;
        this.assetCategory = assetCategory;
        this.network = network;
        this.assetRef = assetRef;
        this.totalQuantity = totalQuantity;
        this.availableQuantity = availableQuantity;
        this.lockedQuantity = lockedQuantity;
        this.valuationStatus = ValuationStatus.UNAVAILABLE;
        this.fetchedAt = fetchedAt;
        this.lastSuccessSyncRunId = lastSuccessSyncRunId;
    }

    public ConnectionEntity getConnection() {
        return connection;
    }

    public String getAssetKey() {
        return assetKey;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public BigDecimal getAvailableQuantity() {
        return availableQuantity;
    }

    public BigDecimal getLockedQuantity() {
        return lockedQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getFxRateToJpy() {
        return fxRateToJpy;
    }

    public BigDecimal getJpyValue() {
        return jpyValue;
    }

    public ValuationStatus getValuationStatus() {
        return valuationStatus;
    }

    public UUID getLastSuccessSyncRunId() {
        return lastSuccessSyncRunId;
    }
}
