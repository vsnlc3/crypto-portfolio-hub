package com.cryptoportfoliohub.sync.api;

import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.entity.SyncRunStatus;
import com.cryptoportfoliohub.persistence.entity.SyncTriggerType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncAcceptedResponse(
        UUID syncRunId,
        UUID connectionId,
        SyncTriggerType triggerType,
        SyncRunStatus status,
        List<SyncCapability> capabilities,
        Instant startedAt) {

    public SyncAcceptedResponse {
        capabilities = List.copyOf(capabilities);
    }
}
