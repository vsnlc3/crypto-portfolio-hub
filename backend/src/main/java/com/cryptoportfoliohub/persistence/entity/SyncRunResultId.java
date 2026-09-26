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
public class SyncRunResultId implements Serializable {

    @Column(name = "sync_run_id", nullable = false)
    private UUID syncRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false, length = 20)
    private SyncCapability capability;

    protected SyncRunResultId() {
    }

    public SyncRunResultId(UUID syncRunId, SyncCapability capability) {
        this.syncRunId = Objects.requireNonNull(syncRunId);
        this.capability = Objects.requireNonNull(capability);
    }

    public UUID getSyncRunId() {
        return syncRunId;
    }

    public SyncCapability getCapability() {
        return capability;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SyncRunResultId that)) return false;
        return syncRunId.equals(that.syncRunId) && capability == that.capability;
    }

    @Override
    public int hashCode() {
        return Objects.hash(syncRunId, capability);
    }
}
