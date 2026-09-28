ALTER TABLE provider_account_states
    DROP CONSTRAINT uq_provider_account_states_connection,
    ADD COLUMN account_scope VARCHAR(191) NOT NULL DEFAULT 'ACCOUNT',
    ADD COLUMN account_mode VARCHAR(30),
    ADD COLUMN provider_abstraction_mode VARCHAR(32),
    ADD CONSTRAINT uq_provider_account_states_connection_scope
        UNIQUE (connection_id, user_id, account_scope),
    ADD CONSTRAINT ck_provider_account_states_account_mode
        CHECK (account_mode IS NULL OR account_mode IN (
            'STANDARD', 'UNIFIED_ACCOUNT', 'PORTFOLIO_MARGIN', 'UNKNOWN', 'UNSUPPORTED')),
    ADD CONSTRAINT ck_provider_account_states_provider_abstraction_mode
        CHECK (provider_abstraction_mode IS NULL OR provider_abstraction_mode IN (
            'disabled', 'unifiedAccount', 'portfolioMargin', 'default', 'dexAbstraction', 'UNKNOWN'));

-- Some HIP-3 market price denominations are not identified by the documented Info metadata.
-- Preserve the position while keeping its unknown price currency unavailable instead of guessing.
ALTER TABLE perpetual_positions
    ALTER COLUMN price_currency DROP NOT NULL;

CREATE TABLE activity_perpetual_fill_details (
    id UUID NOT NULL,
    activity_id UUID NOT NULL,
    instrument_code VARCHAR(191) NOT NULL,
    side VARCHAR(10) NOT NULL,
    direction VARCHAR(24) NOT NULL,
    provider_direction VARCHAR(100),
    quantity NUMERIC(38, 18) NOT NULL,
    price NUMERIC(38, 18) NOT NULL,
    price_currency VARCHAR(8),
    start_position NUMERIC(38, 18),
    closed_pnl NUMERIC(38, 18),
    closed_pnl_currency VARCHAR(8),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_activity_perpetual_fill_details PRIMARY KEY (id),
    CONSTRAINT uq_activity_perp_fill_detail_activity UNIQUE (activity_id),
    CONSTRAINT fk_activity_perp_fill_detail_activity FOREIGN KEY (activity_id)
        REFERENCES activities (id) ON DELETE RESTRICT,
    CONSTRAINT ck_activity_perp_fill_detail_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_activity_perp_fill_detail_direction CHECK (
        direction IN ('OPEN_LONG', 'CLOSE_LONG', 'OPEN_SHORT', 'CLOSE_SHORT', 'UNKNOWN')),
    CONSTRAINT ck_activity_perp_fill_detail_quantity CHECK (quantity > 0),
    CONSTRAINT ck_activity_perp_fill_detail_price CHECK (price > 0)
);
