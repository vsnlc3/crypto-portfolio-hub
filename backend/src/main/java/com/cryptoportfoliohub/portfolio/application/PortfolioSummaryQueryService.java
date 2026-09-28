package com.cryptoportfoliohub.portfolio.application;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cryptoportfoliohub.connection.api.CapabilitySyncResponse;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncState;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStatus;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.portfolio.api.PortfolioSummaryResponse;
import com.cryptoportfoliohub.portfolio.api.PortfolioSummaryStatus;
import com.cryptoportfoliohub.portfolio.domain.ConnectionPortfolioStatus;
import com.cryptoportfoliohub.portfolio.domain.ConnectionPortfolioValuation;
import com.cryptoportfoliohub.portfolio.domain.PortfolioValuation;
import com.cryptoportfoliohub.portfolio.domain.UserPortfolioValuation;

@Service
public class PortfolioSummaryQueryService {

    private final PortfolioValuationService valuationService;
    private final ConnectionRepository connectionRepository;
    private final ConnectionSyncStateRepository syncStateRepository;

    public PortfolioSummaryQueryService(
            PortfolioValuationService valuationService,
            ConnectionRepository connectionRepository,
            ConnectionSyncStateRepository syncStateRepository) {
        this.valuationService = valuationService;
        this.connectionRepository = connectionRepository;
        this.syncStateRepository = syncStateRepository;
    }

    @Transactional
    public PortfolioSummaryResponse getSummary(UUID authenticatedUserId) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId must not be null");
        UserPortfolioValuation valuation = valuationService.valueUserWithConnections(authenticatedUserId);
        List<ConnectionEntity> connections = connectionRepository
                .findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(authenticatedUserId);
        Map<UUID, EnumMap<SyncCapability, ConnectionSyncState>> syncByConnection = groupSyncStates(
                syncStateRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(authenticatedUserId));

        List<PortfolioSummaryResponse.Connection> connectionResponses = connections.stream()
                .map(connection -> connectionResponse(
                        connection,
                        syncByConnection.getOrDefault(connection.getId(), new EnumMap<>(SyncCapability.class)),
                        valuation.connections().getOrDefault(connection.getId(),
                                new ConnectionPortfolioValuation(
                                        Optional.empty(), ConnectionPortfolioStatus.UNAVAILABLE))))
                .toList();
        PortfolioValuation portfolio = valuation.portfolio();
        Instant lastSuccessfulSyncAt = syncByConnection.values().stream()
                .flatMap(states -> states.values().stream())
                .map(ConnectionSyncState::getLastSuccessAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        return new PortfolioSummaryResponse(
                new PortfolioSummaryResponse.Summary(
                        portfolio.netWorthJpy().orElse(null),
                        new PortfolioSummaryResponse.Change24h(
                                null, null, PortfolioSummaryStatus.UNAVAILABLE, null),
                        portfolio.holdingsValueJpy().orElse(null),
                        portfolio.directionalValueJpy().orElse(null),
                        portfolio.stablecoinValueJpy().orElse(null),
                        portfolio.marketExposureJpy().orElse(null),
                        portfolio.exposureRatio().orElse(null),
                        portfolio.unrealizedPnlJpy().orElse(null),
                        summaryStatus(portfolio, connectionResponses),
                        portfolio.dataAsOfAt().orElse(null),
                        lastSuccessfulSyncAt),
                connectionResponses);
    }

    private PortfolioSummaryResponse.Connection connectionResponse(
            ConnectionEntity connection,
            EnumMap<SyncCapability, ConnectionSyncState> syncStates,
            ConnectionPortfolioValuation valuation) {
        List<CapabilitySyncResponse> capabilities = supportedCapabilities(connection.getProvider()).stream()
                .map(capability -> {
                    ConnectionSyncState state = syncStates.get(capability);
                    return state == null
                            ? new CapabilitySyncResponse(capability, ConnectionSyncStatus.NOT_SYNCED,
                                    null, null, null)
                            : new CapabilitySyncResponse(capability, state.getStatus(),
                                    state.getLastAttemptAt(), state.getLastSuccessAt(),
                                    state.getLastErrorCategory());
                })
                .toList();
        Instant lastAttemptAt = capabilities.stream()
                .map(CapabilitySyncResponse::lastAttemptAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        Instant lastSuccessAt = capabilities.stream()
                .map(CapabilitySyncResponse::lastSuccessAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        return new PortfolioSummaryResponse.Connection(
                connection.getId(),
                connection.getProvider(),
                connection.getDisplayName(),
                valuation.amountJpy().orElse(null),
                valuation.status(),
                lastAttemptAt,
                lastSuccessAt,
                capabilities);
    }

    private PortfolioSummaryStatus summaryStatus(
            PortfolioValuation valuation,
            List<PortfolioSummaryResponse.Connection> connections) {
        return switch (valuation.snapshotFreshness()) {
            case FRESH -> PortfolioSummaryStatus.COMPLETE;
            case STALE -> PortfolioSummaryStatus.STALE;
            case UNAVAILABLE -> connections.stream()
                            .map(PortfolioSummaryResponse.Connection::dataStatus)
                            .anyMatch(status -> status != ConnectionPortfolioStatus.UNAVAILABLE)
                    ? PortfolioSummaryStatus.PARTIAL
                    : PortfolioSummaryStatus.UNAVAILABLE;
        };
    }

    private Map<UUID, EnumMap<SyncCapability, ConnectionSyncState>> groupSyncStates(
            List<ConnectionSyncState> syncStates) {
        Map<UUID, EnumMap<SyncCapability, ConnectionSyncState>> grouped = new HashMap<>();
        for (ConnectionSyncState state : syncStates) {
            grouped.computeIfAbsent(state.getId().getConnectionId(), ignored -> new EnumMap<>(SyncCapability.class))
                    .put(state.getId().getCapability(), state);
        }
        return grouped;
    }

    private List<SyncCapability> supportedCapabilities(ConnectionProvider provider) {
        return switch (provider) {
            case BITBANK, SOLANA -> List.of(SyncCapability.BALANCE, SyncCapability.ACTIVITY);
            case HYPERLIQUID -> List.of(
                    SyncCapability.BALANCE,
                    SyncCapability.POSITION,
                    SyncCapability.ACTIVITY,
                    SyncCapability.ACCOUNT);
        };
    }
}
