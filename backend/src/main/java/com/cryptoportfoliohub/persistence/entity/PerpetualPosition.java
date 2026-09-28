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
import com.cryptoportfoliohub.domain.money.PositionSide;

@Entity
@Table(name = "perpetual_positions", uniqueConstraints =
        @UniqueConstraint(name = "uq_perpetual_positions_connection_position",
                columnNames = {"connection_id", "user_id", "position_key"}))
public class PerpetualPosition extends UpdatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "connection_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
    })
    private ConnectionEntity connection;

    @Column(name = "position_key", nullable = false, length = 191)
    private String positionKey;

    @Column(name = "instrument_code", nullable = false, length = 50)
    private String instrumentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private PositionSide side;

    @Column(name = "quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(name = "entry_price", precision = 38, scale = 18)
    private BigDecimal entryPrice;

    @Column(name = "mark_price", precision = 38, scale = 18)
    private BigDecimal markPrice;

    @Column(name = "liquidation_price", precision = 38, scale = 18)
    private BigDecimal liquidationPrice;

    @Column(name = "price_currency", length = 8)
    private String priceCurrency;

    @Column(name = "leverage", precision = 18, scale = 8)
    private BigDecimal leverage;

    @Column(name = "margin_amount", precision = 38, scale = 18)
    private BigDecimal marginAmount;

    @Column(name = "margin_currency", length = 8)
    private String marginCurrency;

    @Column(name = "unrealized_pnl", precision = 38, scale = 18)
    private BigDecimal unrealizedPnl;

    @Column(name = "pnl_currency", length = 8)
    private String pnlCurrency;

    @Column(name = "price_fx_rate_to_jpy", precision = 24, scale = 12)
    private BigDecimal priceFxRateToJpy;

    @Column(name = "price_fx_source", length = 50)
    private String priceFxSource;

    @Column(name = "price_fx_evaluated_at")
    private Instant priceFxEvaluatedAt;

    @Column(name = "margin_fx_rate_to_jpy", precision = 24, scale = 12)
    private BigDecimal marginFxRateToJpy;

    @Column(name = "margin_fx_source", length = 50)
    private String marginFxSource;

    @Column(name = "margin_fx_evaluated_at")
    private Instant marginFxEvaluatedAt;

    @Column(name = "pnl_fx_rate_to_jpy", precision = 24, scale = 12)
    private BigDecimal pnlFxRateToJpy;

    @Column(name = "pnl_fx_source", length = 50)
    private String pnlFxSource;

    @Column(name = "pnl_fx_evaluated_at")
    private Instant pnlFxEvaluatedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "last_success_sync_run_id", nullable = false)
    private UUID lastSuccessSyncRunId;

    protected PerpetualPosition() {
    }

    public PerpetualPosition(
            ConnectionEntity connection,
            String positionKey,
            String instrumentCode,
            PositionSide side,
            BigDecimal quantity,
            BigDecimal entryPrice,
            BigDecimal markPrice,
            BigDecimal liquidationPrice,
            String priceCurrency,
            BigDecimal leverage,
            BigDecimal marginAmount,
            String marginCurrency,
            BigDecimal unrealizedPnl,
            String pnlCurrency,
            Instant fetchedAt,
            UUID lastSuccessSyncRunId) {
        this.connection = connection;
        this.positionKey = positionKey;
        this.instrumentCode = instrumentCode;
        this.side = side;
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.markPrice = markPrice;
        this.liquidationPrice = liquidationPrice;
        this.priceCurrency = priceCurrency;
        this.leverage = leverage;
        this.marginAmount = marginAmount;
        this.marginCurrency = marginCurrency;
        this.unrealizedPnl = unrealizedPnl;
        this.pnlCurrency = pnlCurrency;
        this.fetchedAt = fetchedAt;
        this.lastSuccessSyncRunId = lastSuccessSyncRunId;
    }

    public ConnectionEntity getConnection() {
        return connection;
    }

    public String getPositionKey() {
        return positionKey;
    }

    public PositionSide getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public BigDecimal getLiquidationPrice() {
        return liquidationPrice;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public BigDecimal getLeverage() {
        return leverage;
    }

    public BigDecimal getMarginAmount() {
        return marginAmount;
    }

    public String getMarginCurrency() {
        return marginCurrency;
    }

    public BigDecimal getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public String getPnlCurrency() {
        return pnlCurrency;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public UUID getLastSuccessSyncRunId() {
        return lastSuccessSyncRunId;
    }

    public BigDecimal getPriceFxRateToJpy() {
        return priceFxRateToJpy;
    }

    public BigDecimal getMarginFxRateToJpy() {
        return marginFxRateToJpy;
    }

    public BigDecimal getPnlFxRateToJpy() {
        return pnlFxRateToJpy;
    }
}
