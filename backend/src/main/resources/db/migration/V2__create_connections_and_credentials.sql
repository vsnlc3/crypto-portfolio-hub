CREATE TABLE connections (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    display_name VARCHAR(100),
    external_account_ref VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    last_attempt_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_connections PRIMARY KEY (id),
    CONSTRAINT uq_connections_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_connections_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_connections_provider CHECK (provider IN ('BITBANK', 'SOLANA', 'HYPERLIQUID')),
    CONSTRAINT ck_connections_status CHECK (status IN ('CONNECTED', 'SYNCING', 'ERROR', 'DISCONNECTED'))
);

CREATE TABLE connection_credentials (
    id UUID NOT NULL,
    connection_id UUID NOT NULL,
    user_id UUID NOT NULL,
    credential_type VARCHAR(50) NOT NULL,
    ciphertext BYTEA NOT NULL,
    nonce BYTEA NOT NULL,
    key_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_connection_credentials PRIMARY KEY (id),
    CONSTRAINT uq_connection_credentials_type UNIQUE (connection_id, user_id, credential_type),
    CONSTRAINT fk_connection_credentials_connection FOREIGN KEY (connection_id, user_id)
        REFERENCES connections (id, user_id),
    CONSTRAINT ck_connection_credentials_key_version CHECK (key_version > 0)
);
