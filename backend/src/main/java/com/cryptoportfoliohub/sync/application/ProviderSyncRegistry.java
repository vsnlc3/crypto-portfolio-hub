package com.cryptoportfoliohub.sync.application;

import com.cryptoportfoliohub.error.ResourceNotFoundException;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.SyncTriggerType;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProviderSyncRegistry {

    private final SyncCoordinator syncCoordinator;
    private final ConnectionRepository connectionRepository;
    private final Map<ConnectionProvider, ProviderSyncPort> providers;

    public ProviderSyncRegistry(
            SyncCoordinator syncCoordinator,
            ConnectionRepository connectionRepository,
            List<ProviderSyncPort> providerSyncPorts) {
        this.syncCoordinator = syncCoordinator;
        this.connectionRepository = connectionRepository;
        EnumMap<ConnectionProvider, ProviderSyncPort> indexed = new EnumMap<>(ConnectionProvider.class);
        for (ProviderSyncPort providerSyncPort : providerSyncPorts) {
            if (indexed.putIfAbsent(providerSyncPort.provider(), providerSyncPort) != null) {
                throw new IllegalStateException("A provider sync adapter was registered more than once.");
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public SyncExecutionTicket requestManualSync(UUID authenticatedUserId, UUID connectionId) {
        ConnectionEntity connection = connectionRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(connectionId, authenticatedUserId)
                .orElseThrow(ResourceNotFoundException::new);
        ProviderSyncPort providerSyncPort = providers.get(connection.getProvider());
        if (providerSyncPort == null) {
            throw new SyncProviderNotAvailableException();
        }
        return syncCoordinator.requestSync(
                authenticatedUserId, connectionId, SyncTriggerType.MANUAL, providerSyncPort);
    }
}
