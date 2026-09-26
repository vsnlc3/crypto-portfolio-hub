package com.cryptoportfoliohub.error;

import org.springframework.http.HttpStatus;

public enum ProviderErrorCategory {
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PROVIDER_TIMEOUT"),
    RATE_LIMIT(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_RATE_LIMIT"),
    AUTHENTICATION(HttpStatus.BAD_GATEWAY, "PROVIDER_AUTHENTICATION_FAILED"),
    PERMISSION(HttpStatus.BAD_GATEWAY, "PROVIDER_PERMISSION_DENIED"),
    UNAVAILABLE(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE"),
    INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "PROVIDER_INVALID_RESPONSE");

    private final HttpStatus httpStatus;
    private final String problemCode;

    ProviderErrorCategory(HttpStatus httpStatus, String problemCode) {
        this.httpStatus = httpStatus;
        this.problemCode = problemCode;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String problemCode() {
        return problemCode;
    }
}
