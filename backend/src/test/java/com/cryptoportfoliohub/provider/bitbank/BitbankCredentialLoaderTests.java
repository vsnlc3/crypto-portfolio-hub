package com.cryptoportfoliohub.provider.bitbank;

import com.cryptoportfoliohub.config.CredentialEncryptionProperties;
import com.cryptoportfoliohub.connection.credential.CredentialEncryptionService;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.persistence.entity.ConnectionCredential;
import com.cryptoportfoliohub.persistence.repository.ConnectionCredentialRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BitbankCredentialLoaderTests {

    @Test
    void decryptsCredentialsOnlyThroughOwnerScopedConnectionQuery() {
        UUID connectionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CredentialEncryptionService encryptionService = new CredentialEncryptionService(properties());
        var apiKeyCiphertext = encryptionService.encrypt("fixture-api-key".getBytes(StandardCharsets.UTF_8));
        var apiSecretCiphertext = encryptionService.encrypt("fixture-api-secret".getBytes(StandardCharsets.UTF_8));
        ConnectionCredential apiKeyRow = row("API_KEY", apiKeyCiphertext);
        ConnectionCredential apiSecretRow = row("API_SECRET", apiSecretCiphertext);
        ConnectionCredentialRepository repository = mock(ConnectionCredentialRepository.class);
        when(repository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                connectionId, userId)).thenReturn(List.of(apiKeyRow, apiSecretRow));
        BitbankCredentialLoader loader = new BitbankCredentialLoader(repository, encryptionService);

        BitbankCredentials credentials = loader.load(connectionId, userId);

        assertThat(credentials.apiKeyHeaderValue()).isEqualTo("fixture-api-key");
        assertThat(new String(credentials.copyApiSecret(), StandardCharsets.UTF_8)).isEqualTo("fixture-api-secret");
        assertThat(credentials.toString()).doesNotContain("fixture-api-key", "fixture-api-secret");
        verify(repository).findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                connectionId, userId);
        credentials.close();
        assertThatThrownBy(credentials::apiKeyHeaderValue).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMissingOrMalformedCredentialRowsWithSafeAuthenticationCategory() {
        ConnectionCredentialRepository repository = mock(ConnectionCredentialRepository.class);
        UUID connectionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                connectionId, userId)).thenReturn(List.of());
        BitbankCredentialLoader loader = new BitbankCredentialLoader(
                repository, new CredentialEncryptionService(properties()));

        assertThatThrownBy(() -> loader.load(connectionId, userId))
                .isInstanceOf(ProviderException.class)
                .satisfies(exception -> {
                    ProviderException providerException = (ProviderException) exception;
                    assertThat(providerException.category()).isEqualTo(ProviderErrorCategory.AUTHENTICATION);
                    assertThat(providerException.getMessage()).doesNotContain("credential", "API_SECRET");
                });
    }

    private static ConnectionCredential row(String type, com.cryptoportfoliohub.connection.credential.EncryptedCredential encrypted) {
        ConnectionCredential row = mock(ConnectionCredential.class);
        when(row.getCredentialType()).thenReturn(type);
        when(row.encryptedValue()).thenReturn(encrypted);
        return row;
    }

    private static CredentialEncryptionProperties properties() {
        CredentialEncryptionProperties properties = new CredentialEncryptionProperties();
        properties.setKeyBase64(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setCurrentKeyVersion(1);
        return properties;
    }
}
