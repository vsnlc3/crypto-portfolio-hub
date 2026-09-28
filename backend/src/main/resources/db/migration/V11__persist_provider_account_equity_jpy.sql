ALTER TABLE provider_account_states
    ADD COLUMN account_equity_jpy NUMERIC(38, 8),
    ADD CONSTRAINT ck_provider_account_states_equity_jpy_requires_equity
        CHECK (account_equity_jpy IS NULL OR account_equity IS NOT NULL);
