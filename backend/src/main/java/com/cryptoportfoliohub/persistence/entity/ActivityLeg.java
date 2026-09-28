package com.cryptoportfoliohub.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "activity_legs", uniqueConstraints =
        @UniqueConstraint(name = "uq_activity_legs_activity_index", columnNames = {"activity_id", "leg_index"}))
public class ActivityLeg extends CreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(name = "leg_index", nullable = false)
    private int legIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private ActivityDirection direction;

    @Column(name = "asset_key", nullable = false, length = 191)
    private String assetKey;

    @Column(name = "symbol", length = 32)
    private String symbol;

    @Column(name = "quantity", precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(name = "original_amount", precision = 38, scale = 18)
    private BigDecimal originalAmount;

    @Column(name = "original_currency", length = 8)
    private String originalCurrency;

    @Column(name = "jpy_value", precision = 38, scale = 8)
    private BigDecimal jpyValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "valuation_status", nullable = false, length = 20)
    private ValuationStatus valuationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "valuation_basis", length = 30)
    private ValuationBasis valuationBasis;

    @Column(name = "price_used", precision = 38, scale = 18)
    private BigDecimal priceUsed;

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

    protected ActivityLeg() {
    }

    public ActivityLeg(
            Activity activity,
            int legIndex,
            ActivityDirection direction,
            String assetKey,
            String symbol,
            BigDecimal quantity,
            BigDecimal originalAmount,
            String originalCurrency) {
        this.activity = activity;
        this.legIndex = legIndex;
        this.direction = direction;
        this.assetKey = assetKey;
        this.symbol = symbol;
        this.quantity = quantity;
        this.originalAmount = originalAmount;
        this.originalCurrency = originalCurrency;
        this.valuationStatus = ValuationStatus.UNAVAILABLE;
        this.valuationBasis = ValuationBasis.UNAVAILABLE;
    }

    public Activity getActivity() {
        return activity;
    }

    public int getLegIndex() {
        return legIndex;
    }

    public ActivityDirection getDirection() {
        return direction;
    }

    public String getAssetKey() {
        return assetKey;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public String getOriginalCurrency() {
        return originalCurrency;
    }

    public BigDecimal getJpyValue() {
        return jpyValue;
    }

    public ValuationStatus getValuationStatus() {
        return valuationStatus;
    }
}
