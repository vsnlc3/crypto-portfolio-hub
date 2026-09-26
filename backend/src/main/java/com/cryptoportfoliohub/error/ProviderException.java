package com.cryptoportfoliohub.error;

public class ProviderException extends RuntimeException {

    private final ProviderErrorCategory category;

    public ProviderException(ProviderErrorCategory category) {
        super("A connected provider could not complete the request.");
        this.category = category;
    }

    public ProviderErrorCategory category() {
        return category;
    }
}
