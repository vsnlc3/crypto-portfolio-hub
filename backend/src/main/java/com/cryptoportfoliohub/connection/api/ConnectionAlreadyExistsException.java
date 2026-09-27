package com.cryptoportfoliohub.connection.api;

public class ConnectionAlreadyExistsException extends RuntimeException {

    public ConnectionAlreadyExistsException() {
        super("An active connection for this provider account already exists.");
    }
}
