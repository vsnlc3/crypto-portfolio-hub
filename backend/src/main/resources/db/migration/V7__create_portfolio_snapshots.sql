CREATE TABLE portfolio_snapshots (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    snapshot_at TIMESTAMPTZ NOT NULL,
    data_as_of_at TIMESTAMPTZ NOT NULL,
    net_worth_jpy NUMERIC(38, 8) NOT NULL,
    holdings_value_jpy NUMERIC(38, 8) NOT NULL,
    directional_value_jpy NUMERIC(38, 8) NOT NULL,
    stablecoin_value_jpy NUMERIC(38, 8) NOT NULL,
    market_exposure_jpy NUMERIC(38, 8) NOT NULL,
    unrealized_pnl_jpy NUMERIC(38, 8) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_portfolio_snapshots PRIMARY KEY (id),
    CONSTRAINT fk_portfolio_snapshots_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_portfolio_snapshots_status CHECK (status IN ('COMPLETE', 'STALE'))
);
