package com.cryptoportfoliohub.connection.api;

import java.util.Map;
import java.util.TreeMap;

public class ConnectionRequestValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public ConnectionRequestValidationException(Map<String, String> errors) {
        super("One or more provider-specific connection fields are invalid.");
        this.errors = Map.copyOf(new TreeMap<>(errors));
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
