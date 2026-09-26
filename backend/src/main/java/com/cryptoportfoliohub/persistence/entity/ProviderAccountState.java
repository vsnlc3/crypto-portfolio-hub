package com.cryptoportfoliohub.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "provider_account_states", uniqueConstraints =
        @UniqueConstraint(name = "uq_provider_account_states_connection", columnNames = {"connection_id", "user_id"}))
public class ProviderAccountState extends UpdatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "connection_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
    })
    private ConnectionEntity connection;

    @Column(name = "account_currency", length = 8)
    private String accountCurrency;

    @Column(name = "cash_balance", precision = 38, scale = 18)
    private BigDecimal cashBalance;

    @Column(name = "collateral_balance", precision = 38, scale = 18)
    private BigDecimal collateralBalance;

    @Column(name = "account_equity", precision = 38, scale = 18)
    private BigDecimal accountEquity;

    @Column(name = "unrealized_pnl", precision = 38, scale = 18)
    private BigDecimal unrealizedPnl;

    @Column(name = "equity_includes_unrealized_pnl")
    private Boolean equityIncludesUnrealizedPnl;

    @Column(name = "fx_rate_to_jpy", precision = 24, scale = 12)
    private BigDecimal fxRateToJpy;

    @Column(name = "fx_source", length = 50)
    private String fxSource;

    @Column(name = "fx_evaluated_at")
    private Instant fxEvaluatedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "last_success_sync_run_id", nullable = false)
    private UUID lastSuccessSyncRunId;

    protected ProviderAccountState() {
    }

    public ConnectionEntity getConnection() {
        return connection;
    }

    public String getAccountCurrency() {
        return accountCurrency;
    }

    public BigDecimal getAccountEquity() {
        return accountEquity;
    }

    public BigDecimal getFxRateToJpy() {
        return fxRateToJpy;
    }
}
