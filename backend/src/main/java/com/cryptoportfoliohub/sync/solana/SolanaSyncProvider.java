package com.cryptoportfoliohub.sync.solana;

import com.cryptoportfoliohub.domain.solana.SolanaAddress;
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
import com.cryptoportfoliohub.provider.ActivityProvider;
import com.cryptoportfoliohub.provider.BalanceProvider;
import com.cryptoportfoliohub.provider.NormalizedAssetBalance;
import com.cryptoportfoliohub.sync.application.ProviderSyncPort;
import com.cryptoportfoliohub.sync.application.SyncExecutionTicket;
import com.cryptoportfoliohub.sync.domain.SyncCapabilityOutcome;
import com.cryptoportfoliohub.provider.solana.config.SolanaProviderProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SolanaSyncProvider implements ProviderSyncPort {

    private static final Set<SyncCapability> CAPABILITIES = Set.of(SyncCapability.BALANCE, SyncCapability.ACTIVITY);

    private final ConnectionRepository connectionRepository;
    private final ConnectionSyncStateRepository syncStateRepository;
    private final BalanceProvider balanceProvider;
    private final ActivityProvider activityProvider;
    private final SolanaSyncWriter syncWriter;
    private final SolanaProviderProperties properties;

    public SolanaSyncProvider(
            ConnectionRepository connectionRepository,
            ConnectionSyncStateRepository syncStateRepository,
            BalanceProvider balanceProvider,
            ActivityProvider activityProvider,
            SolanaSyncWriter syncWriter,
            SolanaProviderProperties properties) {
        this.connectionRepository = connectionRepository;
        this.syncStateRepository = syncStateRepository;
        this.balanceProvider = balanceProvider;
        this.activityProvider = activityProvider;
        this.syncWriter = syncWriter;
        this.properties = properties;
    }

    @Override
    public ConnectionProvider provider() {
        return ConnectionProvider.SOLANA;
    }

    @Override
    public Set<SyncCapability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public List<SyncCapabilityOutcome> synchronize(SyncExecutionTicket ticket) {
        List<SyncCapabilityOutcome> outcomes = new ArrayList<>();
        for (SyncCapability capability : ticket.capabilities()) {
            try {
                outcomes.add(switch (capability) {
                    case BALANCE -> syncBalances(ticket);
                    case ACTIVITY -> syncActivity(ticket);
                    case POSITION, ACCOUNT -> SyncCapabilityOutcome.skipped(capability);
                });
            } catch (ProviderException exception) {
                outcomes.add(SyncCapabilityOutcome.failed(capability, exception.category()));
            } catch (RuntimeException exception) {
                outcomes.add(SyncCapabilityOutcome.failed(capability, ProviderErrorCategory.UNAVAILABLE));
            }
        }
        return outcomes;
    }

    private SyncCapabilityOutcome syncBalances(SyncExecutionTicket ticket) {
        ConnectionEntity connection = ownedConnection(ticket);
        List<NormalizedAssetBalance> balances = balanceProvider.fetchBalances(connection.getExternalAccountRef());
        int persisted = syncWriter.replaceBalances(ticket, balances);
        return SyncCapabilityOutcome.success(SyncCapability.BALANCE, balances.size(), persisted);
    }

    private SyncCapabilityOutcome syncActivity(SyncExecutionTicket ticket) {
        ConnectionEntity connection = ownedConnection(ticket);
        ConnectionSyncState state = activitySyncState(ticket);
        String cursor = state.getProviderCursor();
        Instant queryWindowStart;
        if (cursor != null) {
            queryWindowStart = state.getCursorWindowStartAt();
            if (queryWindowStart == null) {
                throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
            }
        } else if (state.getLastSuccessAt() == null) {
            queryWindowStart = floorToSecond(ticket.startedAt().minus(properties.getActivityInitialBackfillWindow()));
        } else {
            queryWindowStart = floorToSecond(state.getLastSuccessAt().minus(properties.getActivitySyncOverlap()));
        }

        ActivityPage page = activityProvider.fetchActivities(
                connection.getExternalAccountRef(), cursor, properties.getActivityPageSize(), queryWindowStart);
        if (cursor != null && cursor.equals(page.nextCursor())) {
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }
        int persisted = syncWriter.persistActivityPage(ticket, page, queryWindowStart);
        return SyncCapabilityOutcome.success(
                SyncCapability.ACTIVITY, page.activities().size(), persisted, page.nextCursor() != null);
    }

    private ConnectionEntity ownedConnection(SyncExecutionTicket ticket) {
        ConnectionEntity connection = connectionRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                        ticket.connectionId(), ticket.userId())
                .orElseThrow(ResourceNotFoundException::new);
        if (connection.getProvider() != ConnectionProvider.SOLANA
                || !SolanaAddress.isValid(connection.getExternalAccountRef())) {
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

    private static Instant floorToSecond(Instant instant) {
        return Instant.ofEpochSecond(instant.getEpochSecond());
    }
}
