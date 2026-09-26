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

    public PortfolioSnapshotStatus getStatus() {
        return status;
    }
}
