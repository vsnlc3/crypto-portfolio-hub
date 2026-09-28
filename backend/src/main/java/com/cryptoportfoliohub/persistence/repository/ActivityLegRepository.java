package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cryptoportfoliohub.persistence.entity.ActivityLeg;

public interface ActivityLegRepository extends JpaRepository<ActivityLeg, UUID> {

    List<ActivityLeg> findAllByActivity_IdAndActivity_Connection_User_IdOrderByLegIndexAsc(
            UUID activityId, UUID authenticatedUserId);

    Optional<ActivityLeg> findByIdAndActivity_Connection_User_Id(UUID id, UUID authenticatedUserId);

    @Query("select leg from ActivityLeg leg join fetch leg.activity activity "
            + "where activity.id in :activityIds and activity.connection.user.id = :authenticatedUserId "
            + "order by activity.id asc, leg.legIndex asc")
    List<ActivityLeg> findAllOwnedByActivityIds(
            @Param("activityIds") List<UUID> activityIds,
            @Param("authenticatedUserId") UUID authenticatedUserId);

    long deleteAllByActivity_Id(UUID activityId);
}
