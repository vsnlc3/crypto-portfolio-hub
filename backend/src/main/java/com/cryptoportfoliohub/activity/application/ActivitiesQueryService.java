package com.cryptoportfoliohub.activity.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cryptoportfoliohub.activity.api.ActivitiesResponse;
import com.cryptoportfoliohub.activity.api.ActivityCursorException;
import com.cryptoportfoliohub.activity.api.ActivityDataStatus;
import com.cryptoportfoliohub.persistence.entity.Activity;
import com.cryptoportfoliohub.persistence.entity.ActivityLeg;
import com.cryptoportfoliohub.persistence.entity.ActivityPerpetualFillDetail;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncState;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStatus;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.repository.ActivityLegRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityPerpetualFillDetailRepository;
import com.cryptoportfoliohub.persistence.repository.ActivityRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;

@Service
public class ActivitiesQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ActivityRepository activityRepository;
    private final ActivityLegRepository activityLegRepository;
    private final ActivityPerpetualFillDetailRepository perpetualFillDetailRepository;
    private final ConnectionRepository connectionRepository;
    private final ConnectionSyncStateRepository syncStateRepository;

    public ActivitiesQueryService(
            ActivityRepository activityRepository,
            ActivityLegRepository activityLegRepository,
            ActivityPerpetualFillDetailRepository perpetualFillDetailRepository,
            ConnectionRepository connectionRepository,
            ConnectionSyncStateRepository syncStateRepository) {
        this.activityRepository = activityRepository;
        this.activityLegRepository = activityLegRepository;
        this.perpetualFillDetailRepository = perpetualFillDetailRepository;
        this.connectionRepository = connectionRepository;
        this.syncStateRepository = syncStateRepository;
    }

    @Transactional(readOnly = true)
    public ActivitiesResponse getActivities(UUID authenticatedUserId, String cursor, int requestedLimit) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId must not be null");
        int limit = normalizeLimit(requestedLimit);
        CursorPosition position = decodeCursor(cursor);
        List<Activity> fetched = position == null
                ? activityRepository.findFirstPageByOwner(authenticatedUserId, PageRequest.of(0, limit + 1))
                : activityRepository.findNextPageByOwner(
                        authenticatedUserId, position.occurredAt(), position.id(), PageRequest.of(0, limit + 1));
        boolean hasMore = fetched.size() > limit;
        List<Activity> page = hasMore ? fetched.subList(0, limit) : fetched;
        List<UUID> activityIds = page.stream().map(Activity::getId).toList();

        Map<UUID, List<ActivityLeg>> legsByActivity = activityIds.isEmpty()
                ? Map.of()
                : activityLegRepository.findAllOwnedByActivityIds(activityIds, authenticatedUserId).stream()
                        .collect(Collectors.groupingBy(leg -> leg.getActivity().getId()));
        Map<UUID, ActivityPerpetualFillDetail> fillByActivity = activityIds.isEmpty()
                ? Map.of()
                : perpetualFillDetailRepository.findAllOwnedByActivityIds(activityIds, authenticatedUserId).stream()
                        .collect(Collectors.toMap(detail -> detail.getActivity().getId(), detail -> detail));
        Map<UUID, ConnectionSyncState> activitySyncByConnection = syncStateRepository
                .findAllByConnection_User_Id(authenticatedUserId).stream()
                .filter(state -> state.getId().getCapability() == SyncCapability.ACTIVITY)
                .collect(Collectors.toMap(state -> state.getId().getConnectionId(), state -> state));

        List<ActivitiesResponse.ActivityItem> items = page.stream()
                .map(activity -> toItem(activity,
                        legsByActivity.getOrDefault(activity.getId(), List.of()),
                        fillByActivity.get(activity.getId()),
                        activitySyncByConnection.get(activity.getConnection().getId())))
                .toList();
        String nextCursor = hasMore && !page.isEmpty() ? encodeCursor(page.getLast()) : null;
        return new ActivitiesResponse(
                summary(authenticatedUserId, activitySyncByConnection), items, nextCursor, hasMore);
    }

    private int normalizeLimit(int requestedLimit) {
        int limit = requestedLimit == 0 ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return limit;
    }

    private ActivitiesResponse.Summary summary(
            UUID authenticatedUserId, Map<UUID, ConnectionSyncState> syncByConnection) {
        List<ConnectionEntity> connections = connectionRepository
                .findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(authenticatedUserId);
        List<ConnectionSyncState> states = connections.stream()
                .map(connection -> syncByConnection.get(connection.getId()))
                .filter(Objects::nonNull)
                .toList();
        long successfulConnections = states.stream()
                .filter(state -> state.getLastSuccessAt() != null)
                .count();
        boolean everyConnectionReady = connections.stream().allMatch(connection -> {
            ConnectionSyncState state = syncByConnection.get(connection.getId());
            return state != null && state.getLastSuccessAt() != null
                    && state.getStatus() == ConnectionSyncStatus.READY;
        });
        Instant lastSuccessAt = states.stream()
                .map(ConnectionSyncState::getLastSuccessAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        ActivityDataStatus status;
        if (connections.isEmpty() || successfulConnections == 0) {
            status = ActivityDataStatus.UNAVAILABLE;
        } else if (successfulConnections < connections.size()) {
            status = ActivityDataStatus.PARTIAL;
        } else if (everyConnectionReady) {
            status = ActivityDataStatus.COMPLETE;
        } else {
            status = ActivityDataStatus.STALE;
        }
        return new ActivitiesResponse.Summary(status, connections.size(), (int) successfulConnections, lastSuccessAt);
    }

    private ActivitiesResponse.ActivityItem toItem(
            Activity activity,
            List<ActivityLeg> legs,
            ActivityPerpetualFillDetail fill,
            ConnectionSyncState syncState) {
        ConnectionEntity connection = activity.getConnection();
        List<ActivitiesResponse.ActivityLegItem> legItems = legs.stream()
                .sorted(Comparator.comparingInt(ActivityLeg::getLegIndex))
                .map(this::toLegItem)
                .toList();
        return new ActivitiesResponse.ActivityItem(
                activity.getId(),
                connection.getId(),
                connection.getProvider(),
                connection.getDisplayName(),
                activity.getProviderEventId(),
                activity.getEventType(),
                activity.getOriginalEventType(),
                activity.getStatus(),
                activity.getOccurredAt(),
                activity.getImportedAt(),
                activityStatus(connection, syncState),
                syncState == null ? null : syncState.getLastSuccessAt(),
                legItems,
                fill == null ? null : toPerpetualFillItem(fill));
    }

    private ActivityDataStatus activityStatus(ConnectionEntity connection, ConnectionSyncState syncState) {
        if (connection.getDeletedAt() != null) {
            return ActivityDataStatus.STALE;
        }
        if (syncState == null || syncState.getLastSuccessAt() == null) {
            return ActivityDataStatus.UNAVAILABLE;
        }
        return syncState.getStatus() == ConnectionSyncStatus.READY
                ? ActivityDataStatus.COMPLETE
                : ActivityDataStatus.STALE;
    }

    private ActivitiesResponse.ActivityLegItem toLegItem(ActivityLeg leg) {
        return new ActivitiesResponse.ActivityLegItem(
                leg.getLegIndex(), leg.getDirection(), leg.getAssetKey(), leg.getSymbol(), leg.getQuantity(),
                leg.getOriginalAmount(), leg.getOriginalCurrency(), leg.getJpyValue(), leg.getValuationStatus(),
                leg.getValuationBasis(), leg.getPriceUsed(), leg.getPriceCurrency(), leg.getPriceSource(),
                leg.getPriceEvaluatedAt(), leg.getFxRateToJpy(), leg.getFxSource(), leg.getFxEvaluatedAt());
    }

    private ActivitiesResponse.PerpetualFillItem toPerpetualFillItem(ActivityPerpetualFillDetail detail) {
        return new ActivitiesResponse.PerpetualFillItem(
                detail.getInstrumentCode(), detail.getSide(), detail.getDirection(), detail.getProviderDirection(),
                detail.getQuantity(), detail.getPrice(), detail.getPriceCurrency(), detail.getStartPosition(),
                detail.getClosedPnl(), detail.getClosedPnlCurrency());
    }

    private CursorPosition decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("cursor part count");
            }
            return new CursorPosition(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new ActivityCursorException();
        }
    }

    private String encodeCursor(Activity activity) {
        String value = activity.getOccurredAt() + "|" + activity.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record CursorPosition(Instant occurredAt, UUID id) {
    }
}
