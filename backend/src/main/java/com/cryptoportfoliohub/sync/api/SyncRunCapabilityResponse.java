package com.cryptoportfoliohub.sync.api;

import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.entity.SyncResultStatus;
import java.time.Instant;

public record SyncRunCapabilityResponse(
        SyncCapability capability,
        SyncResultStatus status,
        Integer recordsFetched,
        Integer recordsPersisted,
        Instant startedAt,
        Instant finishedAt,
        String errorCategory,
        String safeErrorDetail,
        boolean continuationAvailable) {
}
