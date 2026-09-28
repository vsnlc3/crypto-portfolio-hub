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
        @UniqueConstraint(name = "uq_provider_account_states_connection_scope",
                columnNames = {"connection_id", "user_id", "account_scope"}))
public class ProviderAccountState extends UpdatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "connection_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
    })
    private ConnectionEntity connection;

    @Column(name = "account_scope", nullable = false, length = 191)
    private String accountScope;

    @Column(name = "account_mode", length = 30)
    private String accountMode;

    @Column(name = "provider_abstraction_mode", length = 32)
    private String providerAbstractionMode;

    @Column(name = "account_currency", length = 8)
    private String accountCurrency;

    @Column(name = "cash_balance", precision = 38, scale = 18)
    private BigDecimal cashBalance;

    @Column(name = "collateral_balance", precision = 38, scale = 18)
    private BigDecimal collateralBalance;

    @Column(name = "account_equity", precision = 38, scale = 18)
    private BigDecimal accountEquity;

    @Column(name = "account_equity_jpy", precision = 38, scale = 8)
    private BigDecimal accountEquityJpy;

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

    public ProviderAccountState(
            ConnectionEntity connection,
            String accountScope,
            String accountMode,
            String providerAbstractionMode,
            String accountCurrency,
            BigDecimal cashBalance,
            BigDecimal collateralBalance,
            BigDecimal accountEquity,
            BigDecimal unrealizedPnl,
            Boolean equityIncludesUnrealizedPnl,
            Instant fetchedAt,
            UUID lastSuccessSyncRunId) {
        this.connection = connection;
        this.accountScope = accountScope;
        this.accountMode = accountMode;
        this.providerAbstractionMode = providerAbstractionMode;
        this.accountCurrency = accountCurrency;
        this.cashBalance = cashBalance;
        this.collateralBalance = collateralBalance;
        this.accountEquity = accountEquity;
        this.unrealizedPnl = unrealizedPnl;
        this.equityIncludesUnrealizedPnl = equityIncludesUnrealizedPnl;
        this.fetchedAt = fetchedAt;
        this.lastSuccessSyncRunId = lastSuccessSyncRunId;
    }

    public ConnectionEntity getConnection() {
        return connection;
    }

    public String getAccountCurrency() {
        return accountCurrency;
    }

    public String getAccountScope() {
        return accountScope;
    }

    public String getAccountMode() {
        return accountMode;
    }

    public String getProviderAbstractionMode() {
        return providerAbstractionMode;
    }

    public BigDecimal getAccountEquity() {
        return accountEquity;
    }

    public BigDecimal getAccountEquityJpy() {
        return accountEquityJpy;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public BigDecimal getCollateralBalance() {
        return collateralBalance;
    }

    public BigDecimal getFxRateToJpy() {
        return fxRateToJpy;
    }

    public String getFxSource() {
        return fxSource;
    }

    public Instant getFxEvaluatedAt() {
        return fxEvaluatedAt;
    }

    public BigDecimal getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public Boolean getEquityIncludesUnrealizedPnl() {
        return equityIncludesUnrealizedPnl;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public UUID getLastSuccessSyncRunId() {
        return lastSuccessSyncRunId;
    }

    public void recordFxValuation(
            BigDecimal rate, String source, Instant evaluatedAt, BigDecimal accountEquityJpy) {
        this.fxRateToJpy = rate;
        this.fxSource = source;
        this.fxEvaluatedAt = evaluatedAt;
        this.accountEquityJpy = accountEquityJpy;
    }
}
