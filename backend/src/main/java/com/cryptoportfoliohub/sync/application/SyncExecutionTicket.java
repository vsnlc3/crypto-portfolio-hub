package com.cryptoportfoliohub.sync.application;

import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.entity.SyncTriggerType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncExecutionTicket(
        UUID syncRunId,
        UUID userId,
        UUID connectionId,
        SyncTriggerType triggerType,
        List<SyncCapability> capabilities,
        Instant startedAt) {

    public SyncExecutionTicket {
        capabilities = List.copyOf(capabilities);
    }
}
