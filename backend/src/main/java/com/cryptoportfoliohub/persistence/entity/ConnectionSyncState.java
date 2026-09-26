package com.cryptoportfoliohub.persistence.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "connection_sync_states")
public class ConnectionSyncState {

    @EmbeddedId
    private ConnectionSyncStateId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "connection_id", referencedColumnName = "id", insertable = false, updatable = false),
            @JoinColumn(name = "user_id", referencedColumnName = "user_id", insertable = false, updatable = false)
    })
    private ConnectionEntity connection;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConnectionSyncStatus status;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_success_sync_run_id")
    private UUID lastSuccessSyncRunId;

    @Column(name = "last_error_category", length = 50)
    private String lastErrorCategory;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConnectionSyncState() {
    }

    public ConnectionSyncStateId getId() {
        return id;
    }

    public ConnectionEntity getConnection() {
        return connection;
    }

    public ConnectionSyncStatus getStatus() {
        return status;
    }
}
