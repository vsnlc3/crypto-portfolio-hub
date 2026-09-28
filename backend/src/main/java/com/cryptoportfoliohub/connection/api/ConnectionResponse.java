package com.cryptoportfoliohub.connection.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.ConnectionStatus;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;

public record ConnectionResponse(
        UUID id,
        ConnectionProvider provider,
        String displayName,
        String maskedIdentifier,
        ConnectionStatus status,
        List<SyncCapability> capabilities,
        List<CapabilitySyncResponse> capabilitySync,
        ConnectionPortfolioValueResponse portfolioValue,
        Instant lastAttemptAt,
        Instant lastSuccessAt) {

    public ConnectionResponse {
        capabilities = List.copyOf(capabilities);
        capabilitySync = List.copyOf(capabilitySync);
    }
}
