package com.cryptoportfoliohub.provider;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record NormalizedActivity(
        String providerEventId,
        String dedupKey,
        NormalizedActivityType eventType,
        String originalEventType,
        String status,
        Instant occurredAt,
        List<NormalizedActivityLeg> legs) {

    public NormalizedActivity {
        Objects.requireNonNull(providerEventId, "providerEventId must not be null");
        Objects.requireNonNull(dedupKey, "dedupKey must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        legs = List.copyOf(Objects.requireNonNull(legs, "legs must not be null"));
        if (providerEventId.isBlank() || dedupKey.isBlank()) {
            throw new IllegalArgumentException("A normalized activity must have provider identifiers.");
        }
    }
}
