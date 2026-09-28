ALTER TABLE connection_sync_states
    ADD COLUMN provider_cursor TEXT,
    ADD COLUMN cursor_window_start_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_connection_sync_states_cursor_window_pair
        CHECK ((provider_cursor IS NULL) = (cursor_window_start_at IS NULL));

ALTER TABLE sync_run_results
    ADD COLUMN continuation_available BOOLEAN NOT NULL DEFAULT FALSE;
