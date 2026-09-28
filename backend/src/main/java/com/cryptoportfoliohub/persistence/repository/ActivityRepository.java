package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cryptoportfoliohub.persistence.entity.Activity;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    @Query("select activity from Activity activity join fetch activity.connection connection "
            + "where connection.user.id = :authenticatedUserId "
            + "order by activity.occurredAt desc, activity.id desc")
    List<Activity> findFirstPageByOwner(
            @Param("authenticatedUserId") UUID authenticatedUserId, Pageable pageable);

    @Query("select activity from Activity activity join fetch activity.connection connection "
            + "where connection.user.id = :authenticatedUserId "
            + "and (activity.occurredAt < :occurredAt or "
            + "(activity.occurredAt = :occurredAt and activity.id < :activityId)) "
            + "order by activity.occurredAt desc, activity.id desc")
    List<Activity> findNextPageByOwner(
            @Param("authenticatedUserId") UUID authenticatedUserId,
            @Param("occurredAt") java.time.Instant occurredAt,
            @Param("activityId") UUID activityId,
            Pageable pageable);

    List<Activity> findAllByConnection_User_IdOrderByOccurredAtDescIdDesc(UUID authenticatedUserId);

    List<Activity> findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
            UUID connectionId, UUID authenticatedUserId);

    Optional<Activity> findByIdAndConnection_User_Id(UUID id, UUID authenticatedUserId);

    Optional<Activity> findByConnection_IdAndConnection_User_IdAndDedupKey(
            UUID connectionId, UUID authenticatedUserId, String dedupKey);
}
