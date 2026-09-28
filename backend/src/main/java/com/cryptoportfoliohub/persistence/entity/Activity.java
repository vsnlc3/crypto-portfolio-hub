package com.cryptoportfoliohub.persistence.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "activities", uniqueConstraints =
        @UniqueConstraint(name = "uq_activities_connection_dedup",
                columnNames = {"connection_id", "user_id", "dedup_key"}))
public class Activity extends CreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "connection_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
    })
    private ConnectionEntity connection;

    @Column(name = "dedup_key", nullable = false, length = 128)
    private String dedupKey;

    @Column(name = "provider_event_id", length = 255)
    private String providerEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private ActivityType eventType;

    @Column(name = "original_event_type", length = 100)
    private String originalEventType;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    protected Activity() {
    }

    public Activity(
            ConnectionEntity connection,
            String dedupKey,
            String providerEventId,
            ActivityType eventType,
            String originalEventType,
            String status,
            Instant occurredAt,
            Instant importedAt) {
        this.connection = connection;
        this.dedupKey = dedupKey;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.originalEventType = originalEventType;
        this.status = status;
        this.occurredAt = occurredAt;
        this.importedAt = importedAt;
    }

    public ConnectionEntity getConnection() {
        return connection;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public ActivityType getEventType() {
        return eventType;
    }

    public String getStatus() {
        return status;
    }

    public void updateFromProvider(
            ActivityType eventType,
            String originalEventType,
            String status,
            Instant occurredAt,
            Instant importedAt) {
        this.eventType = eventType;
        this.originalEventType = originalEventType;
        this.status = status;
        this.occurredAt = occurredAt;
        this.importedAt = importedAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getImportedAt() {
        return importedAt;
    }
}
