package com.cryptoportfoliohub.persistence.entity;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "activity_perpetual_fill_details", uniqueConstraints =
        @UniqueConstraint(name = "uq_activity_perp_fill_detail_activity", columnNames = "activity_id"))
public class ActivityPerpetualFillDetail extends CreatedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(name = "instrument_code", nullable = false, length = 191)
    private String instrumentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private PerpetualFillSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 24)
    private PerpetualFillDirection direction;

    @Column(name = "provider_direction", length = 100)
    private String providerDirection;

    @Column(name = "quantity", nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(name = "price", nullable = false, precision = 38, scale = 18)
    private BigDecimal price;

    @Column(name = "price_currency", length = 8)
    private String priceCurrency;

    @Column(name = "start_position", precision = 38, scale = 18)
    private BigDecimal startPosition;

    @Column(name = "closed_pnl", precision = 38, scale = 18)
    private BigDecimal closedPnl;

    @Column(name = "closed_pnl_currency", length = 8)
    private String closedPnlCurrency;

    protected ActivityPerpetualFillDetail() {
    }

    public ActivityPerpetualFillDetail(
            Activity activity,
            String instrumentCode,
            PerpetualFillSide side,
            PerpetualFillDirection direction,
            String providerDirection,
            BigDecimal quantity,
            BigDecimal price,
            String priceCurrency,
            BigDecimal startPosition,
            BigDecimal closedPnl,
            String closedPnlCurrency) {
        this.activity = activity;
        this.instrumentCode = instrumentCode;
        this.side = side;
        this.direction = direction;
        this.providerDirection = providerDirection;
        this.quantity = quantity;
        this.price = price;
        this.priceCurrency = priceCurrency;
        this.startPosition = startPosition;
        this.closedPnl = closedPnl;
        this.closedPnlCurrency = closedPnlCurrency;
    }

    public Activity getActivity() {
        return activity;
    }

    public String getInstrumentCode() {
        return instrumentCode;
    }

    public PerpetualFillSide getSide() {
        return side;
    }

    public PerpetualFillDirection getDirection() {
        return direction;
    }

    public String getProviderDirection() {
        return providerDirection;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public BigDecimal getStartPosition() {
        return startPosition;
    }

    public BigDecimal getClosedPnl() {
        return closedPnl;
    }

    public String getClosedPnlCurrency() {
        return closedPnlCurrency;
    }
}
