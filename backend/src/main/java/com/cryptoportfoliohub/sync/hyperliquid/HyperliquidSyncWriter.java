package com.cryptoportfoliohub.sync.hyperliquid;

import com.cryptoportfoliohub.error.ResourceNotFoundException;
import com.cryptoportfoliohub.persistence.entity.Activity;
import com.cryptoportfoliohub.persistence.entity.ActivityDirection;
import com.cryptoportfoliohub.persistence.entity.ActivityLeg;
import com.cryptoportfoliohub.persistence.entity.ActivityPerpetualFillDetail;
import com.cryptoportfoliohub.persistence.entity.ActivityType;
import com.cryptoportfoliohub.persistence.entity.AssetBalance;
import com.cryptoportfoliohub.persistence.entity.AssetCategory;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.PerpetualFillDirection;
import com.cryptoportfoliohub.persistence.entity.PerpetualFillSide;
import com.cryptoportfoliohub.persistence.entity.PerpetualPosition;
import com.cryptoportfoliohub.persistence.entity.ProviderAccountState;
import com.cryptoportfoliohub.persistence.repository.ActivityLegRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityPerpetualFillDetailRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityRepository;
import com.cryptoportfoliohub.persistence.repository.AssetBalanceRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.PerpetualPositionRepository;
import com.cryptoportfoliohub.persistence.repository.ProviderAccountStateRepository;
import com.cryptoportfoliohub.provider.NormalizedActivity;
import com.cryptoportfoliohub.provider.NormalizedActivityLeg;
import com.cryptoportfoliohub.provider.NormalizedAssetBalance;
import com.cryptoportfoliohub.provider.NormalizedPerpetualFillDetail;
import com.cryptoportfoliohub.provider.NormalizedPerpetualPosition;
import com.cryptoportfoliohub.provider.NormalizedProviderAccountState;
import com.cryptoportfoliohub.provider.hyperliquid.HyperliquidCurrentState;
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
public class HyperliquidSyncWriter {

    private static final String GENERIC_SYMBOL = "TOKEN";

    private final ConnectionRepository connectionRepository;
    private final AssetBalanceRepository assetBalanceRepository;
    private final ProviderAccountStateRepository accountStateRepository;
    private final PerpetualPositionRepository positionRepository;
    private final ActivityRepository activityRepository;
    private final ActivityLegRepository activityLegRepository;
    private final ActivityPerpetualFillDetailRepository fillDetailRepository;
    private final Clock clock;

    public HyperliquidSyncWriter(
            ConnectionRepository connectionRepository,
            AssetBalanceRepository assetBalanceRepository,
            ProviderAccountStateRepository accountStateRepository,
            PerpetualPositionRepository positionRepository,
            ActivityRepository activityRepository,
            ActivityLegRepository activityLegRepository,
            ActivityPerpetualFillDetailRepository fillDetailRepository,
            Clock clock) {
        this.connectionRepository = connectionRepository;
        this.assetBalanceRepository = assetBalanceRepository;
        this.accountStateRepository = accountStateRepository;
        this.positionRepository = positionRepository;
        this.activityRepository = activityRepository;
        this.activityLegRepository = activityLegRepository;
        this.fillDetailRepository = fillDetailRepository;
        this.clock = clock;
    }

    @Transactional
    public CurrentStateCounts replaceCurrentState(SyncExecutionTicket ticket, HyperliquidCurrentState state) {
        ConnectionEntity connection = activeConnection(ticket);
        requireUnique(state.spotBalances().stream().map(NormalizedAssetBalance::assetKey).toList(),
                "A Balance response contains duplicate asset identities.");
        requireUnique(state.accountStates().stream().map(NormalizedProviderAccountState::accountScope).toList(),
                "An Account State response contains duplicate scopes.");
        requireUnique(state.positions().stream().map(NormalizedPerpetualPosition::positionKey).toList(),
                "A Position response contains duplicate stable keys.");

        assetBalanceRepository.deleteAllByConnection_IdAndConnection_User_Id(
                ticket.connectionId(), ticket.userId());
        positionRepository.deleteAllByConnection_IdAndConnection_User_Id(ticket.connectionId(), ticket.userId());
        accountStateRepository.deleteAllByConnection_IdAndConnection_User_Id(ticket.connectionId(), ticket.userId());
        assetBalanceRepository.flush();
        positionRepository.flush();
        accountStateRepository.flush();

        List<AssetBalance> balances = state.spotBalances().stream()
                .map(balance -> toAssetBalance(connection, ticket, balance))
                .toList();
        List<ProviderAccountState> accountStates = state.accountStates().stream()
                .map(account -> toAccountState(connection, ticket, account))
                .toList();
        List<PerpetualPosition> positions = state.positions().stream()
                .map(position -> toPosition(connection, ticket, position))
                .toList();
        assetBalanceRepository.saveAll(balances);
        accountStateRepository.saveAll(accountStates);
        positionRepository.saveAll(positions);
        return new CurrentStateCounts(balances.size(), accountStates.size(), positions.size());
    }

    @Transactional
    public int persistActivities(SyncExecutionTicket ticket, List<NormalizedActivity> normalizedActivities) {
        ConnectionEntity connection = activeConnection(ticket);
        requireUnique(normalizedActivities.stream().map(NormalizedActivity::dedupKey).toList(),
                "An Activity response contains duplicate dedup keys.");
        Instant importedAt = clock.instant();
        int persisted = 0;

        for (NormalizedActivity normalized : normalizedActivities) {
            ActivityType eventType = ActivityType.valueOf(normalized.eventType().name());
            Activity activity = activityRepository.findByConnection_IdAndConnection_User_IdAndDedupKey(
                            ticket.connectionId(), ticket.userId(), normalized.dedupKey())
                    .orElse(null);
            boolean isNew = activity == null;
            boolean headerChanged = isNew || activity.getEventType() != eventType
                    || !Objects.equals(activity.getOriginalEventType(), normalized.originalEventType())
                    || !Objects.equals(activity.getStatus(), normalized.status())
                    || !Objects.equals(activity.getOccurredAt(), normalized.occurredAt());
            boolean parserRetry = !isNew && "PARSER_ERROR".equals(activity.getStatus());
            boolean hasStoredDetail = !isNew && fillDetailRepository
                    .findByActivity_IdAndActivity_Connection_IdAndActivity_Connection_User_Id(
                            activity.getId(), ticket.connectionId(), ticket.userId())
                    .isPresent();
            boolean detailNeedsRepair = normalized.perpetualFillDetail() != null && !hasStoredDetail;
            boolean obsoleteDetail = normalized.perpetualFillDetail() == null && hasStoredDetail;
            boolean replacePayload = isNew || parserRetry || detailNeedsRepair || obsoleteDetail;

            if (isNew) {
                activity = new Activity(connection, normalized.dedupKey(), normalized.providerEventId(),
                        eventType, normalized.originalEventType(), normalized.status(),
                        normalized.occurredAt(), importedAt);
            } else if (headerChanged) {
                activity.updateFromProvider(eventType, normalized.originalEventType(), normalized.status(),
                        normalized.occurredAt(), importedAt);
            }

            if (!isNew && replacePayload) {
                activityLegRepository.deleteAllByActivity_Id(activity.getId());
                fillDetailRepository.deleteAllByActivity_IdAndActivity_Connection_User_Id(
                        activity.getId(), ticket.userId());
                activityLegRepository.flush();
                fillDetailRepository.flush();
            }
            if (isNew || headerChanged || replacePayload) {
                activity = activityRepository.saveAndFlush(activity);
            }
            if (!replacePayload) {
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
                        leg.symbol() == null || leg.symbol().isBlank() ? GENERIC_SYMBOL : leg.symbol(),
                        leg.quantity(),
                        leg.originalAmount(),
                        leg.originalCurrency()));
            }
            activityLegRepository.saveAll(legs);
            if (normalized.perpetualFillDetail() != null) {
                fillDetailRepository.save(toFillDetail(activity, normalized.perpetualFillDetail()));
            }
            persisted++;
        }
        return persisted;
    }

    private AssetBalance toAssetBalance(
            ConnectionEntity connection, SyncExecutionTicket ticket, NormalizedAssetBalance balance) {
        return new AssetBalance(
                connection,
                balance.assetKey(),
                balance.symbol() == null || balance.symbol().isBlank() ? GENERIC_SYMBOL : balance.symbol(),
                balance.assetName(),
                AssetCategory.valueOf(balance.category().name()),
                balance.network(),
                balance.assetRef(),
                balance.totalQuantity(),
                balance.availableQuantity(),
                balance.lockedQuantity(),
                balance.fetchedAt(),
                ticket.syncRunId());
    }

    private ProviderAccountState toAccountState(
            ConnectionEntity connection,
            SyncExecutionTicket ticket,
            NormalizedProviderAccountState account) {
        return new ProviderAccountState(
                connection,
                account.accountScope(),
                account.accountMode(),
                account.providerAbstractionMode(),
                account.accountCurrency(),
                account.cashBalance(),
                account.collateralBalance(),
                account.accountEquity(),
                account.unrealizedPnl(),
                account.equityIncludesUnrealizedPnl(),
                account.fetchedAt(),
                ticket.syncRunId());
    }

    private PerpetualPosition toPosition(
            ConnectionEntity connection,
            SyncExecutionTicket ticket,
            NormalizedPerpetualPosition position) {
        return new PerpetualPosition(
                connection,
                position.positionKey(),
                position.instrumentCode(),
                position.side(),
                position.quantity(),
                position.entryPrice(),
                position.markPrice(),
                position.liquidationPrice(),
                position.priceCurrency(),
                position.leverage(),
                position.marginAmount(),
                position.marginCurrency(),
                position.unrealizedPnl(),
                position.pnlCurrency(),
                position.fetchedAt(),
                ticket.syncRunId());
    }

    private ActivityPerpetualFillDetail toFillDetail(Activity activity, NormalizedPerpetualFillDetail detail) {
        return new ActivityPerpetualFillDetail(
                activity,
                detail.instrumentCode(),
                PerpetualFillSide.valueOf(detail.side().name()),
                PerpetualFillDirection.valueOf(detail.direction().name()),
                detail.providerDirection(),
                detail.quantity(),
                detail.price(),
                detail.priceCurrency(),
                detail.startPosition(),
                detail.closedPnl(),
                detail.closedPnlCurrency());
    }

    private ConnectionEntity activeConnection(SyncExecutionTicket ticket) {
        ConnectionEntity connection = connectionRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(ticket.connectionId(), ticket.userId())
                .orElseThrow(ResourceNotFoundException::new);
        if (connection.getProvider() != ConnectionProvider.HYPERLIQUID) {
            throw new ResourceNotFoundException();
        }
        return connection;
    }

    private static void requireUnique(List<String> keys, String message) {
        Set<String> unique = new HashSet<>(keys);
        if (unique.size() != keys.size()) {
            throw new IllegalArgumentException(message);
        }
    }

    public record CurrentStateCounts(int balances, int accountStates, int positions) {
    }
}
