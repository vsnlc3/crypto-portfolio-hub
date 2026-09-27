package com.cryptoportfoliohub.connection.application;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cryptoportfoliohub.connection.api.ConnectionAlreadyExistsException;
import com.cryptoportfoliohub.connection.api.ConnectionCreateRequest;
import com.cryptoportfoliohub.connection.api.ConnectionRequestValidationException;
import com.cryptoportfoliohub.connection.api.ConnectionResponse;
import com.cryptoportfoliohub.connection.credential.CredentialEncryptionService;
import com.cryptoportfoliohub.connection.credential.EncryptedCredential;
import com.cryptoportfoliohub.error.ResourceNotFoundException;
import com.cryptoportfoliohub.persistence.entity.ConnectionCredential;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.ConnectionStatus;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.AssetBalanceRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionCredentialRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.persistence.repository.PerpetualPositionRepository;
import com.cryptoportfoliohub.persistence.repository.ProviderAccountStateRepository;

@Service
public class ConnectionService {

    private static final Pattern HYPERLIQUID_ADDRESS = Pattern.compile("(?i)^0x[0-9a-f]{40}$");
    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private final ConnectionRepository connectionRepository;
    private final ConnectionCredentialRepository credentialRepository;
    private final ConnectionSyncStateRepository syncStateRepository;
    private final AssetBalanceRepository assetBalanceRepository;
    private final PerpetualPositionRepository positionRepository;
    private final ProviderAccountStateRepository accountStateRepository;
    private final CredentialEncryptionService credentialEncryptionService;

    public ConnectionService(
            ConnectionRepository connectionRepository,
            ConnectionCredentialRepository credentialRepository,
            ConnectionSyncStateRepository syncStateRepository,
            AssetBalanceRepository assetBalanceRepository,
            PerpetualPositionRepository positionRepository,
            ProviderAccountStateRepository accountStateRepository,
            CredentialEncryptionService credentialEncryptionService) {
        this.connectionRepository = connectionRepository;
        this.credentialRepository = credentialRepository;
        this.syncStateRepository = syncStateRepository;
        this.assetBalanceRepository = assetBalanceRepository;
        this.positionRepository = positionRepository;
        this.accountStateRepository = accountStateRepository;
        this.credentialEncryptionService = credentialEncryptionService;
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> list(User authenticatedUser) {
        return connectionRepository.findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(authenticatedUser.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ConnectionResponse create(User authenticatedUser, ConnectionCreateRequest request) {
        validateProviderFields(request);
        UUID userId = authenticatedUser.getId();
        ConnectionProvider provider = request.provider();
        String externalAccountRef = accountRef(provider, request);

        boolean duplicate = provider == ConnectionProvider.BITBANK
                ? connectionRepository.existsByUser_IdAndProviderAndDeletedAtIsNull(userId, provider)
                : connectionRepository.existsByUser_IdAndProviderAndExternalAccountRefAndDeletedAtIsNull(
                        userId, provider, externalAccountRef);
        if (duplicate) {
            throw new ConnectionAlreadyExistsException();
        }

        ConnectionEntity connection = connectionRepository.saveAndFlush(new ConnectionEntity(
                authenticatedUser,
                provider,
                displayName(provider, request.displayName()),
                externalAccountRef,
                ConnectionStatus.CONNECTED));

        if (provider == ConnectionProvider.BITBANK) {
            saveCredential(connection, "API_KEY", request.apiKey().trim());
            saveCredential(connection, "API_SECRET", request.apiSecret().trim());
        }

        return toResponse(connection);
    }

    @Transactional
    public void delete(UUID connectionId, User authenticatedUser) {
        UUID userId = authenticatedUser.getId();
        ConnectionEntity connection = connectionRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(connectionId, userId)
                .orElseThrow(ResourceNotFoundException::new);

        credentialRepository.deleteAllByConnection_IdAndConnection_User_Id(connectionId, userId);
        syncStateRepository.deleteAllByConnection_IdAndConnection_User_Id(connectionId, userId);
        assetBalanceRepository.deleteAllByConnection_IdAndConnection_User_Id(connectionId, userId);
        positionRepository.deleteAllByConnection_IdAndConnection_User_Id(connectionId, userId);
        accountStateRepository.deleteAllByConnection_IdAndConnection_User_Id(connectionId, userId);

        connection.softDelete(Instant.now());
        connectionRepository.saveAndFlush(connection);
    }

    private void saveCredential(ConnectionEntity connection, String type, String value) {
        byte[] plaintext = value.getBytes(StandardCharsets.UTF_8);
        try {
            EncryptedCredential encrypted = credentialEncryptionService.encrypt(plaintext);
            credentialRepository.save(new ConnectionCredential(connection, type, encrypted));
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private void validateProviderFields(ConnectionCreateRequest request) {
        if (request == null || request.provider() == null) {
            return; // Bean Validation returns the provider field error before this method is called.
        }

        Map<String, String> errors = new TreeMap<>();
        switch (request.provider()) {
            case BITBANK -> {
                required(errors, "apiKey", request.apiKey());
                required(errors, "apiSecret", request.apiSecret());
                unexpected(errors, "walletAddress", request.walletAddress());
                unexpected(errors, "accountAddress", request.accountAddress());
            }
            case SOLANA -> {
                if (isBlank(request.walletAddress()) || !isValidSolanaAddress(request.walletAddress())) {
                    errors.put("walletAddress", "Invalid value.");
                }
                unexpected(errors, "apiKey", request.apiKey());
                unexpected(errors, "apiSecret", request.apiSecret());
                unexpected(errors, "accountAddress", request.accountAddress());
            }
            case HYPERLIQUID -> {
                if (isBlank(request.accountAddress())
                        || !HYPERLIQUID_ADDRESS.matcher(request.accountAddress().trim()).matches()) {
                    errors.put("accountAddress", "Invalid value.");
                }
                unexpected(errors, "apiKey", request.apiKey());
                unexpected(errors, "apiSecret", request.apiSecret());
                unexpected(errors, "walletAddress", request.walletAddress());
            }
        }
        if (!errors.isEmpty()) {
            throw new ConnectionRequestValidationException(errors);
        }
    }

    private void required(Map<String, String> errors, String field, String value) {
        if (isBlank(value)) {
            errors.put(field, "This field is required.");
        }
    }

    private void unexpected(Map<String, String> errors, String field, String value) {
        if (!isBlank(value)) {
            errors.put(field, "This field is not accepted for the selected provider.");
        }
    }

    private boolean isValidSolanaAddress(String address) {
        String value = address.trim();
        if (value.length() < 32 || value.length() > 44) {
            return false;
        }
        BigInteger decoded = BigInteger.ZERO;
        for (int index = 0; index < value.length(); index++) {
            int digit = BASE58_ALPHABET.indexOf(value.charAt(index));
            if (digit < 0) {
                return false;
            }
            decoded = decoded.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
        }
        int leadingZeroBytes = 0;
        while (leadingZeroBytes < value.length() && value.charAt(leadingZeroBytes) == '1') {
            leadingZeroBytes++;
        }
        int significantBytes = decoded.signum() == 0 ? 0 : (decoded.bitLength() + 7) / 8;
        return leadingZeroBytes + significantBytes == 32;
    }

    private String accountRef(ConnectionProvider provider, ConnectionCreateRequest request) {
        return switch (provider) {
            case BITBANK -> null;
            case SOLANA -> request.walletAddress().trim();
            case HYPERLIQUID -> request.accountAddress().trim().toLowerCase(Locale.ROOT);
        };
    }

    private String displayName(ConnectionProvider provider, String requestedName) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }
        return switch (provider) {
            case BITBANK -> "bitbank";
            case SOLANA -> "Phantom";
            case HYPERLIQUID -> "Hyperliquid";
        };
    }

    private ConnectionResponse toResponse(ConnectionEntity connection) {
        return new ConnectionResponse(
                connection.getId(),
                connection.getProvider(),
                connection.getDisplayName(),
                mask(connection.getProvider(), connection.getExternalAccountRef()),
                connection.getStatus(),
                capabilities(connection.getProvider()),
                connection.getLastAttemptAt(),
                connection.getLastSuccessAt());
    }

    private String mask(ConnectionProvider provider, String identifier) {
        if (identifier == null) {
            return null; // bitbank has no verified public account identifier; credentials are not identifiers.
        }
        if (provider == ConnectionProvider.HYPERLIQUID) {
            return identifier.substring(0, 6) + "…" + identifier.substring(identifier.length() - 4);
        }
        return identifier.substring(0, 5) + "…" + identifier.substring(identifier.length() - 4);
    }

    private List<SyncCapability> capabilities(ConnectionProvider provider) {
        return switch (provider) {
            case BITBANK, SOLANA -> List.of(SyncCapability.BALANCE, SyncCapability.ACTIVITY);
            case HYPERLIQUID -> List.of(
                    SyncCapability.BALANCE, SyncCapability.POSITION,
                    SyncCapability.ACTIVITY, SyncCapability.ACCOUNT);
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
