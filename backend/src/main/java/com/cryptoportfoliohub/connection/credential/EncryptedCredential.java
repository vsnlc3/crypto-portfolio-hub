package com.cryptoportfoliohub.connection.credential;

import java.util.Arrays;
import java.util.Objects;

public final class EncryptedCredential {

    private final byte[] ciphertext;
    private final byte[] nonce;
    private final int keyVersion;

    public EncryptedCredential(byte[] ciphertext, byte[] nonce, int keyVersion) {
        this.ciphertext = Arrays.copyOf(Objects.requireNonNull(ciphertext), ciphertext.length);
        this.nonce = Arrays.copyOf(Objects.requireNonNull(nonce), nonce.length);
        this.keyVersion = keyVersion;
    }

    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }

    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    public int keyVersion() {
        return keyVersion;
    }

    @Override
    public String toString() {
        return "EncryptedCredential[ciphertext=[REDACTED], nonce=[REDACTED], keyVersion="
                + keyVersion + "]";
    }
}
