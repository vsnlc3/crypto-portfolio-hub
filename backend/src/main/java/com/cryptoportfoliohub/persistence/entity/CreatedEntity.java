package com.cryptoportfoliohub.persistence.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

@MappedSuperclass
public abstract class CreatedEntity extends UuidEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    protected CreatedEntity() {
    }

    @PrePersist
    protected void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
