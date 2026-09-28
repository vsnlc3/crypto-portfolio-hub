package com.cryptoportfoliohub.persistence.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cryptoportfoliohub.persistence.entity.ActivityPerpetualFillDetail;

public interface ActivityPerpetualFillDetailRepository extends JpaRepository<ActivityPerpetualFillDetail, UUID> {

    Optional<ActivityPerpetualFillDetail> findByActivity_IdAndActivity_Connection_User_Id(
            UUID activityId, UUID authenticatedUserId);

    Optional<ActivityPerpetualFillDetail> findByActivity_IdAndActivity_Connection_IdAndActivity_Connection_User_Id(
            UUID activityId, UUID connectionId, UUID authenticatedUserId);

    @Query("select detail from ActivityPerpetualFillDetail detail join fetch detail.activity activity "
            + "where activity.id in :activityIds and activity.connection.user.id = :authenticatedUserId")
    List<ActivityPerpetualFillDetail> findAllOwnedByActivityIds(
            @Param("activityIds") List<UUID> activityIds,
            @Param("authenticatedUserId") UUID authenticatedUserId);

    long deleteAllByActivity_IdAndActivity_Connection_User_Id(UUID activityId, UUID authenticatedUserId);
}
