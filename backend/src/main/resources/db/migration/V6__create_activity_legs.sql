CREATE TABLE activity_legs (
    id UUID NOT NULL,
    activity_id UUID NOT NULL,
    leg_index INTEGER NOT NULL,
    direction VARCHAR(10) NOT NULL,
    asset_key VARCHAR(191) NOT NULL,
    symbol VARCHAR(32),
    quantity NUMERIC(38, 18),
    original_amount NUMERIC(38, 18),
    original_currency VARCHAR(8),
    jpy_value NUMERIC(38, 8),
    valuation_status VARCHAR(20) NOT NULL,
    valuation_basis VARCHAR(30),
    price_used NUMERIC(38, 18),
    price_currency VARCHAR(8),
    price_source VARCHAR(50),
    price_evaluated_at TIMESTAMPTZ,
    fx_rate_to_jpy NUMERIC(24, 12),
    fx_source VARCHAR(50),
    fx_evaluated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_activity_legs PRIMARY KEY (id),
    CONSTRAINT uq_activity_legs_activity_index UNIQUE (activity_id, leg_index),
    CONSTRAINT fk_activity_legs_activity FOREIGN KEY (activity_id) REFERENCES activities (id),
    CONSTRAINT ck_activity_legs_index CHECK (leg_index >= 0),
    CONSTRAINT ck_activity_legs_direction CHECK (direction IN ('IN', 'OUT', 'FEE')),
    CONSTRAINT ck_activity_legs_quantity CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT ck_activity_legs_jpy_value CHECK (jpy_value IS NULL OR jpy_value >= 0),
    CONSTRAINT ck_activity_legs_fx_rate CHECK (fx_rate_to_jpy IS NULL OR fx_rate_to_jpy > 0),
    CONSTRAINT ck_activity_legs_valuation_status CHECK (valuation_status IN ('VALUED', 'UNAVAILABLE')),
    CONSTRAINT ck_activity_legs_valuation_value CHECK (
        (valuation_status = 'VALUED' AND jpy_value IS NOT NULL)
        OR (valuation_status = 'UNAVAILABLE' AND jpy_value IS NULL)
    ),
    CONSTRAINT ck_activity_legs_valuation_basis CHECK (
        valuation_basis IS NULL
        OR valuation_basis IN ('PROVIDER_REPORTED', 'EVENT_TIME_MARKET', 'IMPORT_TIME_MARKET', 'UNAVAILABLE')
    )
);
