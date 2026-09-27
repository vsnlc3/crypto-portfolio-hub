package com.cryptoportfoliohub.connection.credential;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import com.cryptoportfoliohub.config.CredentialEncryptionProperties;

@Component
public final class CredentialEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int AES_256_KEY_BYTES = 32;
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final CredentialEncryptionProperties properties;
    private final SecureRandom secureRandom;

    public CredentialEncryptionService(CredentialEncryptionProperties properties) {
        this.properties = Objects.requireNonNull(properties);
        this.secureRandom = new SecureRandom();
    }

    public EncryptedCredential encrypt(byte[] plaintext) {
        Objects.requireNonNull(plaintext, "Credential plaintext is required.");
        int keyVersion = requireSupportedCurrentKeyVersion();
        SecretKeySpec key = currentKey();
        byte[] nonce = new byte[GCM_NONCE_BYTES];
        secureRandom.nextBytes(nonce);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new EncryptedCredential(cipher.doFinal(plaintext), nonce, keyVersion);
        } catch (GeneralSecurityException exception) {
            throw new CredentialEncryptionException("Credential encryption failed.");
        }
    }

    public byte[] decrypt(EncryptedCredential encryptedCredential) {
        Objects.requireNonNull(encryptedCredential, "Encrypted credential is required.");
        int keyVersion = encryptedCredential.keyVersion();
        int currentKeyVersion = requireSupportedCurrentKeyVersion();
        if (keyVersion != currentKeyVersion) {
            throw new CredentialEncryptionException("Credential key version is not available.");
        }

        SecretKeySpec key = currentKey();
        byte[] nonce = encryptedCredential.nonce();
        if (nonce.length != GCM_NONCE_BYTES) {
            throw new CredentialEncryptionException("Credential could not be decrypted.");
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return cipher.doFinal(encryptedCredential.ciphertext());
        } catch (GeneralSecurityException exception) {
            throw new CredentialEncryptionException("Credential could not be decrypted.");
        }
    }

    private int requireSupportedCurrentKeyVersion() {
        int keyVersion = properties.getCurrentKeyVersion();
        if (keyVersion < 1) {
            throw new CredentialEncryptionException("Credential key version must be positive.");
        }
        return keyVersion;
    }

    private SecretKeySpec currentKey() {
        String configuredKey = properties.getKeyBase64();
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new CredentialEncryptionException("Credential encryption key is not configured.");
        }

        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException exception) {
            throw new CredentialEncryptionException("Credential encryption key is invalid.");
        }

        if (keyBytes.length != AES_256_KEY_BYTES) {
            Arrays.fill(keyBytes, (byte) 0);
            throw new CredentialEncryptionException("Credential encryption key must be 32 bytes.");
        }
        SecretKeySpec key = new SecretKeySpec(keyBytes, ALGORITHM);
        Arrays.fill(keyBytes, (byte) 0);
        return key;
    }
}
