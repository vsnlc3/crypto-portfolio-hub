package com.cryptoportfoliohub.sync.api;

import com.cryptoportfoliohub.persistence.entity.SyncRunStatus;
import com.cryptoportfoliohub.persistence.entity.SyncTriggerType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncRunResponse(
        UUID syncRunId,
        UUID connectionId,
        SyncTriggerType triggerType,
        SyncRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        String errorCategory,
        String safeErrorDetail,
        List<SyncRunCapabilityResponse> capabilities) {

    public SyncRunResponse {
        capabilities = List.copyOf(capabilities);
    }
}
