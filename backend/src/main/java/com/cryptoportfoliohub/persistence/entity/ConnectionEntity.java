package com.cryptoportfoliohub.persistence.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "connections", uniqueConstraints =
        @UniqueConstraint(name = "uq_connections_id_user", columnNames = {"id", "user_id"}))
public class ConnectionEntity extends UpdatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private ConnectionProvider provider;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "external_account_ref", length = 255)
    private String externalAccountRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConnectionStatus status;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ConnectionEntity() {
    }

    public ConnectionEntity(
            User user,
            ConnectionProvider provider,
            String displayName,
            String externalAccountRef,
            ConnectionStatus status) {
        this.user = user;
        this.provider = provider;
        this.displayName = displayName;
        this.externalAccountRef = externalAccountRef;
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public UUID getUserId() {
        return userId;
    }

    public ConnectionProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExternalAccountRef() {
        return externalAccountRef;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void softDelete(Instant deletedAt) {
        this.deletedAt = deletedAt;
        this.status = ConnectionStatus.DISCONNECTED;
    }
}
