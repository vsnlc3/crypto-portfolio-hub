package com.cryptoportfoliohub.persistence.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@MappedSuperclass
public abstract class UpdatedEntity extends CreatedEntity {

    @Column(name = "updated_at", nullable = false)
    protected Instant updatedAt;

    protected UpdatedEntity() {
    }

    @PrePersist
    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
