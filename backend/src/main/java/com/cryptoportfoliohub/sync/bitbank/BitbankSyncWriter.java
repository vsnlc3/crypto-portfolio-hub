package com.cryptoportfoliohub.sync.bitbank;

import com.cryptoportfoliohub.error.ResourceNotFoundException;
import com.cryptoportfoliohub.persistence.entity.Activity;
import com.cryptoportfoliohub.persistence.entity.ActivityDirection;
import com.cryptoportfoliohub.persistence.entity.ActivityLeg;
import com.cryptoportfoliohub.persistence.entity.ActivityType;
import com.cryptoportfoliohub.persistence.entity.AssetBalance;
import com.cryptoportfoliohub.persistence.entity.AssetCategory;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.repository.ActivityLegRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityRepository;
import com.cryptoportfoliohub.persistence.repository.AssetBalanceRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
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
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BitbankSyncWriter {

    private final ConnectionRepository connectionRepository;
    private final AssetBalanceRepository assetBalanceRepository;
    private final ActivityRepository activityRepository;
    private final ActivityLegRepository activityLegRepository;
    private final Clock clock;

    public BitbankSyncWriter(
            ConnectionRepository connectionRepository,
            AssetBalanceRepository assetBalanceRepository,
            ActivityRepository activityRepository,
            ActivityLegRepository activityLegRepository,
            Clock clock) {
        this.connectionRepository = connectionRepository;
        this.assetBalanceRepository = assetBalanceRepository;
        this.activityRepository = activityRepository;
        this.activityLegRepository = activityLegRepository;
        this.clock = clock;
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
                                ? balance.assetKey() : balance.symbol(),
                        balance.assetName(),
                        AssetCategory.valueOf(balance.category().name()),
                        balance.network(),
                        balance.assetRef(),
                        balance.totalQuantity(),
                        balance.availableQuantity(),
                        balance.lockedQuantity(),
                        balance.fetchedAt(),
                        ticket.syncRunId()))
                .toList();
        assetBalanceRepository.saveAll(rows);
        return rows.size();
    }

    @Transactional
    public int persistActivities(SyncExecutionTicket ticket, List<NormalizedActivity> normalizedActivities) {
        ConnectionEntity connection = activeConnection(ticket);
        Set<String> dedupKeys = new HashSet<>();
        Instant importedAt = clock.instant();
        int persisted = 0;

        for (NormalizedActivity normalized : normalizedActivities) {
            if (!dedupKeys.add(normalized.dedupKey())) {
                throw new IllegalArgumentException("An Activity response contains duplicate dedup keys.");
            }
            ActivityType eventType = ActivityType.valueOf(normalized.eventType().name());
            Activity activity = activityRepository.findByConnection_IdAndConnection_User_IdAndDedupKey(
                            ticket.connectionId(), ticket.userId(), normalized.dedupKey())
                    .orElse(null);
            boolean isNew = activity == null;
            boolean replaceLegs = isNew || "PARSER_ERROR".equals(activity.getStatus());
            boolean headerChanged = isNew || activity.getEventType() != eventType
                    || !Objects.equals(activity.getOriginalEventType(), normalized.originalEventType())
                    || !Objects.equals(activity.getStatus(), normalized.status())
                    || !Objects.equals(activity.getOccurredAt(), normalized.occurredAt());
            if (activity == null) {
                activity = new Activity(connection, normalized.dedupKey(), normalized.providerEventId(),
                        eventType, normalized.originalEventType(), normalized.status(),
                        normalized.occurredAt(), importedAt);
            } else {
                if (headerChanged) {
                    activity.updateFromProvider(eventType, normalized.originalEventType(), normalized.status(),
                            normalized.occurredAt(), importedAt);
                }
                if (replaceLegs) {
                    activityLegRepository.deleteAllByActivity_Id(activity.getId());
                    activityLegRepository.flush();
                }
            }
            if (headerChanged || replaceLegs) {
                activity = activityRepository.saveAndFlush(activity);
            }
            if (!replaceLegs) {
                if (headerChanged) {
                    persisted++;
                }
                continue;
            }

            List<ActivityLeg> legs = new ArrayList<>();
            for (NormalizedActivityLeg leg : normalized.legs()) {
                legs.add(new ActivityLeg(
                        activity,
                        leg.legIndex(),
                        ActivityDirection.valueOf(leg.direction().name()),
                        leg.assetKey(),
                        leg.symbol(),
                        leg.quantity(),
                        leg.originalAmount(),
                        leg.originalCurrency()));
            }
            activityLegRepository.saveAll(legs);
            persisted++;
        }
        return persisted;
    }

    private ConnectionEntity activeConnection(SyncExecutionTicket ticket) {
        ConnectionEntity connection = connectionRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(ticket.connectionId(), ticket.userId())
                .orElseThrow(ResourceNotFoundException::new);
        if (connection.getProvider() != ConnectionProvider.BITBANK) {
            throw new ResourceNotFoundException();
        }
        return connection;
    }
}
