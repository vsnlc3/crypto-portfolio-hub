package com.cryptoportfoliohub.sync.hyperliquid;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.error.ResourceNotFoundException;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncState;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStateId;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.provider.ActivityPage;
import com.cryptoportfoliohub.provider.NormalizedActivity;
import com.cryptoportfoliohub.provider.hyperliquid.HyperliquidAdapter;
import com.cryptoportfoliohub.provider.hyperliquid.HyperliquidCurrentState;
import com.cryptoportfoliohub.sync.application.ProviderSyncPort;
import com.cryptoportfoliohub.sync.application.SyncExecutionTicket;
import com.cryptoportfoliohub.sync.domain.SyncCapabilityOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class HyperliquidSyncProvider implements ProviderSyncPort {

    private static final Duration INITIAL_ACTIVITY_WINDOW = Duration.ofDays(90);
    private static final Duration ACTIVITY_OVERLAP = Duration.ofHours(1);
    private static final Set<SyncCapability> CAPABILITIES = Set.of(
            SyncCapability.BALANCE, SyncCapability.POSITION, SyncCapability.ACTIVITY, SyncCapability.ACCOUNT);
    private static final Set<SyncCapability> CURRENT_STATE_CAPABILITIES = Set.of(
            SyncCapability.BALANCE, SyncCapability.POSITION, SyncCapability.ACCOUNT);

    private final ConnectionRepository connectionRepository;
    private final ConnectionSyncStateRepository syncStateRepository;
    private final HyperliquidAdapter hyperliquidAdapter;
    private final HyperliquidSyncWriter syncWriter;

    public HyperliquidSyncProvider(
            ConnectionRepository connectionRepository,
            ConnectionSyncStateRepository syncStateRepository,
            HyperliquidAdapter hyperliquidAdapter,
            HyperliquidSyncWriter syncWriter) {
        this.connectionRepository = connectionRepository;
        this.syncStateRepository = syncStateRepository;
        this.hyperliquidAdapter = hyperliquidAdapter;
        this.syncWriter = syncWriter;
    }

    @Override
    public ConnectionProvider provider() {
        return ConnectionProvider.HYPERLIQUID;
    }

    @Override
    public Set<SyncCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public List<SyncCapabilityOutcome> synchronize(SyncExecutionTicket ticket) {
        List<SyncCapabilityOutcome> outcomes = new ArrayList<>();
        Set<SyncCapability> requestedCurrentState = EnumSet.noneOf(SyncCapability.class);
        requestedCurrentState.addAll(ticket.capabilities());
        requestedCurrentState.retainAll(CURRENT_STATE_CAPABILITIES);
        if (!requestedCurrentState.isEmpty()) {
            synchronizeCurrentState(ticket, requestedCurrentState, outcomes);
        }
        if (ticket.capabilities().contains(SyncCapability.ACTIVITY)) {
            outcomes.add(synchronizeActivities(ticket));
        }
        return List.copyOf(outcomes);
    }

    private void synchronizeCurrentState(
            SyncExecutionTicket ticket,
            Set<SyncCapability> requested,
            List<SyncCapabilityOutcome> outcomes) {
        try {
            ConnectionEntity connection = ownedConnection(ticket);
            HyperliquidCurrentState state = hyperliquidAdapter.fetchCurrentState(connection.getExternalAccountRef());
            HyperliquidSyncWriter.CurrentStateCounts counts = syncWriter.replaceCurrentState(ticket, state);
            if (requested.contains(SyncCapability.BALANCE)) {
                outcomes.add(SyncCapabilityOutcome.success(
                        SyncCapability.BALANCE, state.spotBalances().size(), counts.balances()));
            }
            if (requested.contains(SyncCapability.ACCOUNT)) {
                outcomes.add(SyncCapabilityOutcome.success(
                        SyncCapability.ACCOUNT, state.accountStates().size(), counts.accountStates()));
            }
            if (requested.contains(SyncCapability.POSITION)) {
                outcomes.add(SyncCapabilityOutcome.success(
                        SyncCapability.POSITION, state.positions().size(), counts.positions()));
            }
        } catch (ProviderException exception) {
            failCurrentStateCapabilities(requested, exception.category(), outcomes);
        } catch (RuntimeException exception) {
            failCurrentStateCapabilities(requested, ProviderErrorCategory.UNAVAILABLE, outcomes);
        }
    }

    private static void failCurrentStateCapabilities(
            Set<SyncCapability> requested,
            ProviderErrorCategory category,
            List<SyncCapabilityOutcome> outcomes) {
        for (SyncCapability capability : List.of(
                SyncCapability.BALANCE, SyncCapability.ACCOUNT, SyncCapability.POSITION)) {
            if (requested.contains(capability)) {
                outcomes.add(SyncCapabilityOutcome.failed(capability, category));
            }
        }
    }

    private SyncCapabilityOutcome synchronizeActivities(SyncExecutionTicket ticket) {
        try {
            ConnectionEntity connection = ownedConnection(ticket);
            ConnectionSyncState state = activitySyncState(ticket);
            Instant fromInclusive = state.getLastSuccessAt() == null
                    ? ticket.startedAt().minus(INITIAL_ACTIVITY_WINDOW)
                    : state.getLastSuccessAt().minus(ACTIVITY_OVERLAP);
            fromInclusive = floorToMillis(fromInclusive);
            Instant toInclusive = floorToMillis(ticket.startedAt());
            ActivityPage page = hyperliquidAdapter.fetchActivities(
                    connection.getExternalAccountRef(), fromInclusive, toInclusive);
            int persisted = syncWriter.persistActivities(ticket, page.activities());
            if (page.limitedByProviderHistory()) {
                return SyncCapabilityOutcome.failed(SyncCapability.ACTIVITY, ProviderErrorCategory.UNAVAILABLE);
            }
            return SyncCapabilityOutcome.success(SyncCapability.ACTIVITY, page.activities().size(), persisted);
        } catch (ProviderException exception) {
            return SyncCapabilityOutcome.failed(SyncCapability.ACTIVITY, exception.category());
        } catch (RuntimeException exception) {
            return SyncCapabilityOutcome.failed(SyncCapability.ACTIVITY, ProviderErrorCategory.UNAVAILABLE);
        }
    }

    private ConnectionEntity ownedConnection(SyncExecutionTicket ticket) {
        ConnectionEntity connection = connectionRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(ticket.connectionId(), ticket.userId())
                .orElseThrow(ResourceNotFoundException::new);
        if (connection.getProvider() != ConnectionProvider.HYPERLIQUID) {
            throw new ResourceNotFoundException();
        }
        return connection;
    }

    private ConnectionSyncState activitySyncState(SyncExecutionTicket ticket) {
        ConnectionSyncStateId id = new ConnectionSyncStateId(
                ticket.connectionId(), ticket.userId(), SyncCapability.ACTIVITY);
        return syncStateRepository.findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(id, ticket.userId())
                .orElseThrow(ResourceNotFoundException::new);
    }

    private static Instant floorToMillis(Instant instant) {
        return Instant.ofEpochMilli(instant.toEpochMilli());
    }
}
