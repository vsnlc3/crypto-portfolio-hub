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
        List<NormalizedActivityLeg> legs,
        NormalizedPerpetualFillDetail perpetualFillDetail) {

    public NormalizedActivity(
            String providerEventId,
            String dedupKey,
            NormalizedActivityType eventType,
            String originalEventType,
            String status,
            Instant occurredAt,
            List<NormalizedActivityLeg> legs) {
        this(providerEventId, dedupKey, eventType, originalEventType, status, occurredAt, legs, null);
    }

    public NormalizedActivity {
        Objects.requireNonNull(providerEventId, "providerEventId must not be null");
        Objects.requireNonNull(dedupKey, "dedupKey must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        legs = List.copyOf(Objects.requireNonNull(legs, "legs must not be null"));
        if (providerEventId.isBlank() || dedupKey.isBlank()) {
            throw new IllegalArgumentException("A normalized activity must have provider identifiers.");
        }
        if (perpetualFillDetail != null && eventType != NormalizedActivityType.PERP) {
            throw new IllegalArgumentException("A perpetual fill detail requires a PERP activity.");
        }
    }
}
