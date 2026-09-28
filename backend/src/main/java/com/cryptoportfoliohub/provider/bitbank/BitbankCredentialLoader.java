package com.cryptoportfoliohub.provider.bitbank;

import com.cryptoportfoliohub.connection.credential.CredentialEncryptionService;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.persistence.entity.ConnectionCredential;
import com.cryptoportfoliohub.persistence.repository.ConnectionCredentialRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class BitbankCredentialLoader {

    private final ConnectionCredentialRepository credentialRepository;
    private final CredentialEncryptionService encryptionService;

    public BitbankCredentialLoader(
            ConnectionCredentialRepository credentialRepository,
            CredentialEncryptionService encryptionService) {
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
    }

    public BitbankCredentials load(UUID connectionId, UUID authenticatedUserId) {
        if (connectionId == null || authenticatedUserId == null) {
            throw new IllegalArgumentException("Connection and authenticated user identifiers are required.");
        }

        List<ConnectionCredential> rows = credentialRepository
                .findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                        connectionId, authenticatedUserId);
        byte[] apiKey = null;
        byte[] apiSecret = null;
        try {
            for (ConnectionCredential row : rows) {
                byte[] decrypted = encryptionService.decrypt(row.encryptedValue());
                if ("API_KEY".equals(row.getCredentialType()) && apiKey == null) {
                    apiKey = decrypted;
                } else if ("API_SECRET".equals(row.getCredentialType()) && apiSecret == null) {
                    apiSecret = decrypted;
                } else {
                    Arrays.fill(decrypted, (byte) 0);
                    throw invalidCredential();
                }
            }
            if (apiKey == null || apiSecret == null || apiKey.length == 0 || apiSecret.length == 0) {
                throw invalidCredential();
            }
            return new BitbankCredentials(apiKey, apiSecret);
        } catch (RuntimeException exception) {
            if (exception instanceof ProviderException providerException) {
                throw providerException;
            }
            throw invalidCredential();
        } finally {
            if (apiKey != null) {
                Arrays.fill(apiKey, (byte) 0);
            }
            if (apiSecret != null) {
                Arrays.fill(apiSecret, (byte) 0);
            }
        }
    }

    private static ProviderException invalidCredential() {
        return new ProviderException(ProviderErrorCategory.AUTHENTICATION);
    }
}
