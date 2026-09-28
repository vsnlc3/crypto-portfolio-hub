package com.cryptoportfoliohub.sync.application;

public class SyncProviderNotAvailableException extends RuntimeException {

    public SyncProviderNotAvailableException() {
        super("Synchronization is not available for this provider yet.");
    }
}
