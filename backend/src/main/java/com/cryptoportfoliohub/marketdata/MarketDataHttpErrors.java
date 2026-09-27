package com.cryptoportfoliohub.marketdata;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;

public final class MarketDataHttpErrors {

    private MarketDataHttpErrors() {
    }

    public static ProviderErrorCategory forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ProviderErrorCategory.AUTHENTICATION;
            case 403 -> ProviderErrorCategory.PERMISSION;
            case 408 -> ProviderErrorCategory.TIMEOUT;
            case 429 -> ProviderErrorCategory.RATE_LIMIT;
            default -> status.is5xxServerError()
                    ? ProviderErrorCategory.UNAVAILABLE
                    : ProviderErrorCategory.INVALID_RESPONSE;
        };
    }

    public static ProviderErrorCategory forTransport(ResourceAccessException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return ProviderErrorCategory.TIMEOUT;
            }
        }
        return ProviderErrorCategory.UNAVAILABLE;
    }
}
