package com.cryptoportfoliohub.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
@Access(AccessType.FIELD)
public class ConnectionSyncStateId implements Serializable {

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false, length = 20)
    private SyncCapability capability;

    protected ConnectionSyncStateId() {
    }

    public ConnectionSyncStateId(UUID connectionId, UUID userId, SyncCapability capability) {
        this.connectionId = Objects.requireNonNull(connectionId);
        this.userId = Objects.requireNonNull(userId);
        this.capability = Objects.requireNonNull(capability);
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public SyncCapability getCapability() {
        return capability;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ConnectionSyncStateId that)) return false;
        return connectionId.equals(that.connectionId)
                && userId.equals(that.userId)
                && capability == that.capability;
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, userId, capability);
    }
}
