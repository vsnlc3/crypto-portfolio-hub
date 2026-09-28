package com.cryptoportfoliohub.connection.api;

import java.time.Instant;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStatus;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;

public record CapabilitySyncResponse(
        SyncCapability capability,
        ConnectionSyncStatus status,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        String lastErrorCategory) {
}
