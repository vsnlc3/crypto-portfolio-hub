package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.ActivityLeg;

public interface ActivityLegRepository extends JpaRepository<ActivityLeg, UUID> {

    List<ActivityLeg> findAllByActivity_IdAndActivity_Connection_User_IdOrderByLegIndexAsc(
            UUID activityId, UUID authenticatedUserId);

    Optional<ActivityLeg> findByIdAndActivity_Connection_User_Id(UUID id, UUID authenticatedUserId);

    long deleteAllByActivity_Id(UUID activityId);
}
