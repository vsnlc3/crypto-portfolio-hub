package com.cryptoportfoliohub.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.ActivityPerpetualFillDetail;

public interface ActivityPerpetualFillDetailRepository extends JpaRepository<ActivityPerpetualFillDetail, UUID> {

    Optional<ActivityPerpetualFillDetail> findByActivity_IdAndActivity_Connection_User_Id(
            UUID activityId, UUID authenticatedUserId);

    Optional<ActivityPerpetualFillDetail> findByActivity_IdAndActivity_Connection_IdAndActivity_Connection_User_Id(
            UUID activityId, UUID connectionId, UUID authenticatedUserId);

    long deleteAllByActivity_IdAndActivity_Connection_User_Id(UUID activityId, UUID authenticatedUserId);
}
