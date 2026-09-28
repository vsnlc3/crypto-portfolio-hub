package com.cryptoportfoliohub.sync.bitbank;

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
import com.cryptoportfoliohub.provider.NormalizedActivity;
import com.cryptoportfoliohub.provider.NormalizedAssetBalance;
import com.cryptoportfoliohub.provider.bitbank.BitbankAdapter;
import com.cryptoportfoliohub.sync.application.ProviderSyncPort;
import com.cryptoportfoliohub.sync.application.SyncExecutionTicket;
import com.cryptoportfoliohub.sync.domain.SyncCapabilityOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BitbankSyncProvider implements ProviderSyncPort {

    private static final Duration INITIAL_ACTIVITY_WINDOW = Duration.ofDays(90);
    private static final Duration ACTIVITY_OVERLAP = Duration.ofHours(1);
    private static final Set<SyncCapability> CAPABILITIES = Set.of(
            SyncCapability.BALANCE, SyncCapability.ACTIVITY);

    private final ConnectionRepository connectionRepository;
    private final ConnectionSyncStateRepository syncStateRepository;
    private final BitbankAdapter bitbankAdapter;
    private final BitbankSyncWriter syncWriter;

    public BitbankSyncProvider(
            ConnectionRepository connectionRepository,
            ConnectionSyncStateRepository syncStateRepository,
            BitbankAdapter bitbankAdapter,
            BitbankSyncWriter syncWriter) {
        this.connectionRepository = connectionRepository;
        this.syncStateRepository = syncStateRepository;
        this.bitbankAdapter = bitbankAdapter;
        this.syncWriter = syncWriter;
    }

    @Override
    public ConnectionProvider provider() {
        return ConnectionProvider.BITBANK;
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
                    case BALANCE -> synchronizeBalances(ticket);
                    case ACTIVITY -> synchronizeActivities(ticket);
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

    private SyncCapabilityOutcome synchronizeBalances(SyncExecutionTicket ticket) {
        ConnectionEntity connection = ownedConnection(ticket);
        List<NormalizedAssetBalance> balances = bitbankAdapter.fetchBalances(connection.getId(), ticket.userId());
        int persisted = syncWriter.replaceBalances(ticket, balances);
        return SyncCapabilityOutcome.success(SyncCapability.BALANCE, balances.size(), persisted);
    }

    private SyncCapabilityOutcome synchronizeActivities(SyncExecutionTicket ticket) {
        ConnectionEntity connection = ownedConnection(ticket);
        ConnectionSyncState state = activitySyncState(ticket);
        Instant fromInclusive = state.getLastSuccessAt() == null
                ? ticket.startedAt().minus(INITIAL_ACTIVITY_WINDOW)
                : state.getLastSuccessAt().minus(ACTIVITY_OVERLAP);
        fromInclusive = floorToMillis(fromInclusive);
        Instant toInclusive = floorToMillis(ticket.startedAt());
        List<NormalizedActivity> activities = bitbankAdapter.fetchActivities(
                connection.getId(), ticket.userId(), fromInclusive, toInclusive);
        int persisted = syncWriter.persistActivities(ticket, activities);
        return SyncCapabilityOutcome.success(SyncCapability.ACTIVITY, activities.size(), persisted);
    }

    private ConnectionEntity ownedConnection(SyncExecutionTicket ticket) {
        ConnectionEntity connection = connectionRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(ticket.connectionId(), ticket.userId())
                .orElseThrow(ResourceNotFoundException::new);
        if (connection.getProvider() != ConnectionProvider.BITBANK) {
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
