package com.cryptoportfoliohub.provider.bitbank;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Holds decrypted credentials only for the lifetime of one provider operation. */
public final class BitbankCredentials implements AutoCloseable {

    private byte[] apiKey;
    private byte[] apiSecret;

    BitbankCredentials(byte[] apiKey, byte[] apiSecret) {
        this.apiKey = Arrays.copyOf(Objects.requireNonNull(apiKey), apiKey.length);
        this.apiSecret = Arrays.copyOf(Objects.requireNonNull(apiSecret), apiSecret.length);
    }

    String apiKeyHeaderValue() {
        ensureOpen();
        return new String(apiKey, StandardCharsets.UTF_8);
    }

    byte[] copyApiSecret() {
        ensureOpen();
        return Arrays.copyOf(apiSecret, apiSecret.length);
    }

    @Override
    public void close() {
        if (apiKey != null) {
            Arrays.fill(apiKey, (byte) 0);
            apiKey = null;
        }
        if (apiSecret != null) {
            Arrays.fill(apiSecret, (byte) 0);
            apiSecret = null;
        }
    }

    @Override
    public String toString() {
        return "BitbankCredentials[apiKey=[REDACTED], apiSecret=[REDACTED]]";
    }

    private void ensureOpen() {
        if (apiKey == null || apiSecret == null) {
            throw new IllegalStateException("Bitbank credentials have been cleared.");
        }
    }
}
