CREATE INDEX idx_connections_user_deleted
    ON connections (user_id, deleted_at);

CREATE UNIQUE INDEX uq_connections_active_external_ref
    ON connections (user_id, provider, external_account_ref)
    WHERE deleted_at IS NULL
      AND external_account_ref IS NOT NULL;

CREATE INDEX idx_sync_runs_user_connection_started
    ON sync_runs (user_id, connection_id, started_at DESC, id DESC);

CREATE INDEX idx_activities_user_occurred
    ON activities (user_id, occurred_at DESC, id DESC);

CREATE INDEX idx_portfolio_snapshots_user_time
    ON portfolio_snapshots (user_id, snapshot_at DESC, id DESC);
