package com.cryptoportfoliohub.connection.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;

/** Provider-specific create fields share one input shape; the provider selects which fields are accepted. */
public record ConnectionCreateRequest(
        @NotNull ConnectionProvider provider,
        @Size(max = 100) String displayName,
        @Size(max = 255) String apiKey,
        @Size(max = 1024) String apiSecret,
        @Size(max = 44) String walletAddress,
        @Size(max = 42) String accountAddress) {

    @Override
    public String toString() {
        return "ConnectionCreateRequest[provider=" + provider
                + ", displayName=[REDACTED], apiKey=[REDACTED], apiSecret=[REDACTED],"
                + " walletAddress=[REDACTED], accountAddress=[REDACTED]]";
    }
}
