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

    @Column(name = "provider_cursor", columnDefinition = "text")
    private String providerCursor;

    @Column(name = "cursor_window_start_at")
    private Instant cursorWindowStartAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConnectionSyncState() {
    }

    public ConnectionSyncState(ConnectionEntity connection, SyncCapability capability, Instant attemptedAt) {
        this.id = new ConnectionSyncStateId(connection.getId(), connection.getUserId(), capability);
        this.connection = connection;
        this.status = ConnectionSyncStatus.SYNCING;
        this.lastAttemptAt = attemptedAt;
        this.updatedAt = attemptedAt;
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

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public String getLastErrorCategory() {
        return lastErrorCategory;
    }

    public UUID getLastSuccessSyncRunId() {
        return lastSuccessSyncRunId;
    }

    public String getProviderCursor() {
        return providerCursor;
    }

    public Instant getCursorWindowStartAt() {
        return cursorWindowStartAt;
    }

    public void updateProviderCursor(String cursor, Instant windowStartAt) {
        if (cursor == null || cursor.isBlank()) {
            this.providerCursor = null;
            this.cursorWindowStartAt = null;
            return;
        }
        if (windowStartAt == null) {
            throw new IllegalArgumentException("A provider cursor requires the query window it belongs to.");
        }
        this.providerCursor = cursor;
        this.cursorWindowStartAt = windowStartAt;
    }

    public void startAttempt(Instant attemptedAt) {
        this.status = ConnectionSyncStatus.SYNCING;
        this.lastAttemptAt = attemptedAt;
        this.lastErrorCategory = null;
        this.updatedAt = attemptedAt;
    }

    public void recordSuccess(UUID syncRunId, Instant succeededAt) {
        this.status = ConnectionSyncStatus.READY;
        this.lastSuccessAt = succeededAt;
        this.lastSuccessSyncRunId = syncRunId;
        this.lastErrorCategory = null;
        this.updatedAt = succeededAt;
    }

    public void recordFailure(String errorCategory, Instant failedAt) {
        this.status = ConnectionSyncStatus.ERROR;
        this.lastErrorCategory = errorCategory;
        this.updatedAt = failedAt;
    }

    public void recordSkipped(Instant finishedAt) {
        this.status = lastSuccessAt == null ? ConnectionSyncStatus.NOT_SYNCED : ConnectionSyncStatus.READY;
        this.lastErrorCategory = null;
        this.updatedAt = finishedAt;
    }
}
