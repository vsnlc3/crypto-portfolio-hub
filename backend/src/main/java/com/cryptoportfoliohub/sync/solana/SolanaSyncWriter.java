package com.cryptoportfoliohub.sync.solana;

import com.cryptoportfoliohub.domain.solana.SolanaAddress;
import com.cryptoportfoliohub.error.ResourceNotFoundException;
import com.cryptoportfoliohub.persistence.entity.Activity;
import com.cryptoportfoliohub.persistence.entity.ActivityDirection;
import com.cryptoportfoliohub.persistence.entity.ActivityLeg;
import com.cryptoportfoliohub.persistence.entity.ActivityType;
import com.cryptoportfoliohub.persistence.entity.AssetBalance;
import com.cryptoportfoliohub.persistence.entity.AssetCategory;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncState;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStateId;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.repository.ActivityLegRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityRepository;
import com.cryptoportfoliohub.persistence.repository.AssetBalanceRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.provider.ActivityPage;
import com.cryptoportfoliohub.provider.NormalizedActivity;
import com.cryptoportfoliohub.provider.NormalizedActivityLeg;
import com.cryptoportfoliohub.provider.NormalizedAssetBalance;
import com.cryptoportfoliohub.provider.NormalizedDirection;
import com.cryptoportfoliohub.sync.application.SyncExecutionTicket;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolanaSyncWriter {

    private static final String GENERIC_TOKEN_SYMBOL = "TOKEN";

    private final ConnectionRepository connectionRepository;
    private final ConnectionSyncStateRepository syncStateRepository;
    private final AssetBalanceRepository assetBalanceRepository;
    private final ActivityRepository activityRepository;
    private final ActivityLegRepository activityLegRepository;
    private final Clock clock;

    public SolanaSyncWriter(
            ConnectionRepository connectionRepository,
            ConnectionSyncStateRepository syncStateRepository,
            AssetBalanceRepository assetBalanceRepository,
            ActivityRepository activityRepository,
            ActivityLegRepository activityLegRepository,
            Clock marketDataClock) {
        this.connectionRepository = connectionRepository;
        this.syncStateRepository = syncStateRepository;
        this.assetBalanceRepository = assetBalanceRepository;
        this.activityRepository = activityRepository;
        this.activityLegRepository = activityLegRepository;
        this.clock = marketDataClock;
    }

    @Transactional
    public int replaceBalances(SyncExecutionTicket ticket, List<NormalizedAssetBalance> balances) {
        ConnectionEntity connection = activeConnection(ticket);
        Set<String> assetKeys = new HashSet<>();
        for (NormalizedAssetBalance balance : balances) {
            if (!assetKeys.add(balance.assetKey())) {
                throw new IllegalArgumentException("A Balance response contains duplicate asset identities.");
            }
        }

        assetBalanceRepository.deleteAllByConnection_IdAndConnection_User_Id(
                ticket.connectionId(), ticket.userId());
        assetBalanceRepository.flush();
        List<AssetBalance> rows = balances.stream()
                .map(balance -> new AssetBalance(
                        connection,
                        balance.assetKey(),
                        balance.symbol() == null || balance.symbol().isBlank()
                                ? GENERIC_TOKEN_SYMBOL : balance.symbol(),
                        balance.assetName(),
                        AssetCategory.valueOf(balance.category().name()),
                        balance.network(),
                        balance.assetRef(),
                        balance.totalQuantity(),
                        balance.fetchedAt(),
                        ticket.syncRunId()))
                .toList();
        assetBalanceRepository.saveAll(rows);
        return rows.size();
    }

    @Transactional
    public int persistActivityPage(SyncExecutionTicket ticket, ActivityPage page, Instant queryWindowStart) {
        ConnectionEntity connection = activeConnection(ticket);
        ConnectionSyncState state = activitySyncState(ticket);
        Set<String> dedupKeys = new HashSet<>();
        int persisted = 0;
        Instant importedAt = clock.instant();

        for (NormalizedActivity normalized : page.activities()) {
            if (!dedupKeys.add(normalized.dedupKey())) {
                throw new IllegalArgumentException("An Activity page contains duplicate dedup keys.");
            }
            ActivityType eventType = ActivityType.valueOf(normalized.eventType().name());
            Activity activity = activityRepository.findByConnection_IdAndConnection_User_IdAndDedupKey(
                            ticket.connectionId(), ticket.userId(), normalized.dedupKey())
                    .orElse(null);
            boolean shouldReplaceLegs = activity == null || "PARSER_ERROR".equals(activity.getStatus());
            if (!shouldReplaceLegs) {
                continue;
            }
            if (activity == null) {
                activity = new Activity(connection, normalized.dedupKey(), normalized.providerEventId(),
                        eventType, normalized.originalEventType(), normalized.status(),
                        normalized.occurredAt(), importedAt);
            } else {
                activity.updateFromProvider(eventType, normalized.originalEventType(), normalized.status(),
                        normalized.occurredAt(), importedAt);
                activityLegRepository.deleteAllByActivity_Id(activity.getId());
                activityLegRepository.flush();
            }
            activity = activityRepository.saveAndFlush(activity);
            List<ActivityLeg> legs = new ArrayList<>();
            for (NormalizedActivityLeg leg : normalized.legs()) {
                legs.add(new ActivityLeg(
                        activity,
                        leg.legIndex(),
                        direction(leg.direction()),
                        leg.assetKey(),
                        leg.symbol(),
                        leg.quantity(),
                        leg.originalAmount(),
                        leg.originalCurrency()));
            }
            activityLegRepository.saveAll(legs);
            persisted++;
        }

        state.updateProviderCursor(page.nextCursor(), page.nextCursor() == null ? null : queryWindowStart);
        return persisted;
    }

    private ConnectionEntity activeConnection(SyncExecutionTicket ticket) {
        ConnectionEntity connection = connectionRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                        ticket.connectionId(), ticket.userId())
                .orElseThrow(ResourceNotFoundException::new);
        if (!SolanaAddress.isValid(connection.getExternalAccountRef())) {
            throw new IllegalArgumentException("The stored Solana address is invalid.");
        }
        return connection;
    }

    private ConnectionSyncState activitySyncState(SyncExecutionTicket ticket) {
        ConnectionSyncStateId stateId = new ConnectionSyncStateId(
                ticket.connectionId(), ticket.userId(), SyncCapability.ACTIVITY);
        return syncStateRepository.findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
                        stateId, ticket.userId())
                .orElseThrow(ResourceNotFoundException::new);
    }

    private static ActivityDirection direction(NormalizedDirection direction) {
        return ActivityDirection.valueOf(direction.name());
    }
}
