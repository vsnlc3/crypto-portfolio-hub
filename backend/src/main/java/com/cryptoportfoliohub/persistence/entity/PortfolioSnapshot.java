package com.cryptoportfoliohub.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "portfolio_snapshots")
public class PortfolioSnapshot extends CreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    @Column(name = "data_as_of_at", nullable = false)
    private Instant dataAsOfAt;

    @Column(name = "net_worth_jpy", nullable = false, precision = 38, scale = 8)
    private BigDecimal netWorthJpy;

    @Column(name = "holdings_value_jpy", nullable = false, precision = 38, scale = 8)
    private BigDecimal holdingsValueJpy;

    @Column(name = "directional_value_jpy", nullable = false, precision = 38, scale = 8)
    private BigDecimal directionalValueJpy;

    @Column(name = "stablecoin_value_jpy", nullable = false, precision = 38, scale = 8)
    private BigDecimal stablecoinValueJpy;

    @Column(name = "market_exposure_jpy", nullable = false, precision = 38, scale = 8)
    private BigDecimal marketExposureJpy;

    @Column(name = "unrealized_pnl_jpy", nullable = false, precision = 38, scale = 8)
    private BigDecimal unrealizedPnlJpy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private PortfolioSnapshotStatus status;

    protected PortfolioSnapshot() {
    }

    public PortfolioSnapshot(
            User user,
            Instant snapshotAt,
            Instant dataAsOfAt,
            BigDecimal netWorthJpy,
            BigDecimal holdingsValueJpy,
            BigDecimal directionalValueJpy,
            BigDecimal stablecoinValueJpy,
            BigDecimal marketExposureJpy,
            BigDecimal unrealizedPnlJpy,
            PortfolioSnapshotStatus status) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.snapshotAt = Objects.requireNonNull(snapshotAt, "snapshotAt must not be null");
        this.dataAsOfAt = Objects.requireNonNull(dataAsOfAt, "dataAsOfAt must not be null");
        this.netWorthJpy = Objects.requireNonNull(netWorthJpy, "netWorthJpy must not be null");
        this.holdingsValueJpy = Objects.requireNonNull(holdingsValueJpy, "holdingsValueJpy must not be null");
        this.directionalValueJpy = Objects.requireNonNull(directionalValueJpy, "directionalValueJpy must not be null");
        this.stablecoinValueJpy = Objects.requireNonNull(stablecoinValueJpy, "stablecoinValueJpy must not be null");
        this.marketExposureJpy = Objects.requireNonNull(marketExposureJpy, "marketExposureJpy must not be null");
        this.unrealizedPnlJpy = Objects.requireNonNull(unrealizedPnlJpy, "unrealizedPnlJpy must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public User getUser() {
        return user;
    }

    public Instant getSnapshotAt() {
        return snapshotAt;
    }

    public Instant getDataAsOfAt() {
        return dataAsOfAt;
    }

    public BigDecimal getNetWorthJpy() {
        return netWorthJpy;
    }

    public BigDecimal getHoldingsValueJpy() {
        return holdingsValueJpy;
    }

    public BigDecimal getDirectionalValueJpy() {
        return directionalValueJpy;
    }

    public BigDecimal getStablecoinValueJpy() {
        return stablecoinValueJpy;
    }

    public BigDecimal getMarketExposureJpy() {
        return marketExposureJpy;
    }

    public BigDecimal getUnrealizedPnlJpy() {
        return unrealizedPnlJpy;
    }

    public PortfolioSnapshotStatus getStatus() {
        return status;
    }
}
