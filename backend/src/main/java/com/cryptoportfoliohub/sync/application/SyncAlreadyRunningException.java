package com.cryptoportfoliohub.sync.application;

public class SyncAlreadyRunningException extends RuntimeException {

    public SyncAlreadyRunningException() {
        super("A synchronization run is already active for this connection.");
    }
}
