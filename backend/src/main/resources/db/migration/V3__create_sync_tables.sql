CREATE TABLE sync_runs (
    id UUID NOT NULL,
    connection_id UUID NOT NULL,
    user_id UUID NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    error_category VARCHAR(50),
    safe_error_detail VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sync_runs PRIMARY KEY (id),
    CONSTRAINT uq_sync_runs_id_connection_user UNIQUE (id, connection_id, user_id),
    CONSTRAINT fk_sync_runs_connection FOREIGN KEY (connection_id, user_id)
        REFERENCES connections (id, user_id),
    CONSTRAINT ck_sync_runs_trigger_type CHECK (trigger_type IN ('INITIAL', 'MANUAL', 'SCHEDULED')),
    CONSTRAINT ck_sync_runs_status CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED'))
);

CREATE TABLE sync_run_results (
    sync_run_id UUID NOT NULL,
    capability VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    records_fetched INTEGER,
    records_persisted INTEGER,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_category VARCHAR(50),
    safe_error_detail VARCHAR(500),
    CONSTRAINT pk_sync_run_results PRIMARY KEY (sync_run_id, capability),
    CONSTRAINT fk_sync_run_results_sync_run FOREIGN KEY (sync_run_id) REFERENCES sync_runs (id),
    CONSTRAINT ck_sync_run_results_capability CHECK (capability IN ('BALANCE', 'POSITION', 'ACTIVITY', 'ACCOUNT')),
    CONSTRAINT ck_sync_run_results_status CHECK (status IN ('SUCCESS', 'FAILED', 'SKIPPED'))
);

CREATE TABLE connection_sync_states (
    connection_id UUID NOT NULL,
    user_id UUID NOT NULL,
    capability VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_attempt_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_success_sync_run_id UUID,
    last_error_category VARCHAR(50),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_connection_sync_states PRIMARY KEY (connection_id, user_id, capability),
    CONSTRAINT fk_connection_sync_states_connection FOREIGN KEY (connection_id, user_id)
        REFERENCES connections (id, user_id),
    CONSTRAINT fk_connection_sync_states_last_run FOREIGN KEY (last_success_sync_run_id, connection_id, user_id)
        REFERENCES sync_runs (id, connection_id, user_id),
    CONSTRAINT ck_connection_sync_states_capability CHECK (capability IN ('BALANCE', 'POSITION', 'ACTIVITY', 'ACCOUNT')),
    CONSTRAINT ck_connection_sync_states_status CHECK (status IN ('NOT_SYNCED', 'SYNCING', 'READY', 'ERROR'))
);
