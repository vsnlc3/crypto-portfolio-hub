package com.cryptoportfoliohub.connection.credential;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import com.cryptoportfoliohub.config.CredentialEncryptionProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialEncryptionServiceTests {

    private static final byte[] PLAINTEXT = "fixture-provider-secret".getBytes(StandardCharsets.UTF_8);

    @Test
    void encryptsAndDecryptsWithAes256GcmAndStoresKeyVersion() {
        CredentialEncryptionService service = new CredentialEncryptionService(configuredProperties());

        EncryptedCredential encrypted = service.encrypt(PLAINTEXT);

        assertThat(encrypted.ciphertext()).hasSize(PLAINTEXT.length + 16);
        assertThat(encrypted.nonce()).hasSize(12);
        assertThat(encrypted.keyVersion()).isEqualTo(3);
        assertThat(encrypted.ciphertext()).isNotEqualTo(PLAINTEXT);
        assertThat(service.decrypt(encrypted)).containsExactly(PLAINTEXT);
    }

    @Test
    void generatesANewNonceForEveryEncryption() {
        CredentialEncryptionService service = new CredentialEncryptionService(configuredProperties());

        EncryptedCredential first = service.encrypt(PLAINTEXT);
        EncryptedCredential second = service.encrypt(PLAINTEXT);

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void rejectsModifiedCiphertext() {
        CredentialEncryptionService service = new CredentialEncryptionService(configuredProperties());
        EncryptedCredential encrypted = service.encrypt(PLAINTEXT);
        byte[] modifiedCiphertext = encrypted.ciphertext();
        modifiedCiphertext[0] ^= 0x01;

        assertThatThrownBy(() -> service.decrypt(
                new EncryptedCredential(modifiedCiphertext, encrypted.nonce(), encrypted.keyVersion())))
                .isInstanceOf(CredentialEncryptionException.class)
                .hasMessage("Credential could not be decrypted.");
    }

    @Test
    void rejectsModifiedAuthenticationTag() {
        CredentialEncryptionService service = new CredentialEncryptionService(configuredProperties());
        EncryptedCredential encrypted = service.encrypt(PLAINTEXT);
        byte[] modifiedCiphertext = encrypted.ciphertext();
        modifiedCiphertext[modifiedCiphertext.length - 1] ^= 0x01;

        assertThatThrownBy(() -> service.decrypt(
                new EncryptedCredential(modifiedCiphertext, encrypted.nonce(), encrypted.keyVersion())))
                .isInstanceOf(CredentialEncryptionException.class)
                .hasMessage("Credential could not be decrypted.");
    }

    @Test
    void failsClosedWhenKeyIsNotConfigured() {
        CredentialEncryptionService service = new CredentialEncryptionService(new CredentialEncryptionProperties());

        assertThatThrownBy(() -> service.encrypt(PLAINTEXT))
                .isInstanceOf(CredentialEncryptionException.class)
                .hasMessage("Credential encryption key is not configured.");
    }

    @Test
    void rejectsInvalidKeyEncodingAndKeyLength() {
        CredentialEncryptionProperties invalidEncoding = configuredProperties();
        invalidEncoding.setKeyBase64("not base64!");
        CredentialEncryptionProperties invalidLength = configuredProperties();
        invalidLength.setKeyBase64(Base64.getEncoder().encodeToString(new byte[16]));

        assertThatThrownBy(() -> new CredentialEncryptionService(invalidEncoding).encrypt(PLAINTEXT))
                .isInstanceOf(CredentialEncryptionException.class)
                .hasMessage("Credential encryption key is invalid.");
        assertThatThrownBy(() -> new CredentialEncryptionService(invalidLength).encrypt(PLAINTEXT))
                .isInstanceOf(CredentialEncryptionException.class)
                .hasMessage("Credential encryption key must be 32 bytes.");
    }

    @Test
    void rejectsUnavailableKeyVersion() {
        CredentialEncryptionService service = new CredentialEncryptionService(configuredProperties());
        EncryptedCredential encrypted = service.encrypt(PLAINTEXT);

        assertThatThrownBy(() -> service.decrypt(
                new EncryptedCredential(encrypted.ciphertext(), encrypted.nonce(), 2)))
                .isInstanceOf(CredentialEncryptionException.class)
                .hasMessage("Credential key version is not available.");
    }

    @Test
    void encryptedValueDefensivelyCopiesItsByteArrays() {
        byte[] ciphertext = {1, 2, 3};
        byte[] nonce = {4, 5, 6};
        EncryptedCredential encrypted = new EncryptedCredential(ciphertext, nonce, 1);
        Arrays.fill(ciphertext, (byte) 9);
        Arrays.fill(nonce, (byte) 9);

        assertThat(encrypted.ciphertext()).containsExactly(1, 2, 3);
        assertThat(encrypted.nonce()).containsExactly(4, 5, 6);
    }

    private CredentialEncryptionProperties configuredProperties() {
        CredentialEncryptionProperties properties = new CredentialEncryptionProperties();
        properties.setKeyBase64(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setCurrentKeyVersion(3);
        return properties;
    }
}
