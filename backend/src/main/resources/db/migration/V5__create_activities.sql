CREATE TABLE activities (
    id UUID NOT NULL,
    connection_id UUID NOT NULL,
    user_id UUID NOT NULL,
    dedup_key VARCHAR(128) NOT NULL,
    provider_event_id VARCHAR(255),
    event_type VARCHAR(30) NOT NULL,
    original_event_type VARCHAR(100),
    status VARCHAR(30),
    occurred_at TIMESTAMPTZ NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_activities PRIMARY KEY (id),
    CONSTRAINT uq_activities_connection_dedup UNIQUE (connection_id, user_id, dedup_key),
    CONSTRAINT fk_activities_connection FOREIGN KEY (connection_id, user_id)
        REFERENCES connections (id, user_id),
    CONSTRAINT ck_activities_event_type CHECK (
        event_type IN ('BUY', 'SELL', 'DEPOSIT', 'WITHDRAW', 'TRANSFER', 'SWAP', 'PERP', 'FUNDING', 'OTHER')
    )
);
