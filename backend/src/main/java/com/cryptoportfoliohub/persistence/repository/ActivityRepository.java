package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.Activity;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    List<Activity> findAllByConnection_User_IdOrderByOccurredAtDescIdDesc(UUID authenticatedUserId);

    List<Activity> findAllByConnection_IdAndConnection_User_IdOrderByOccurredAtDescIdDesc(
            UUID connectionId, UUID authenticatedUserId);

    Optional<Activity> findByIdAndConnection_User_Id(UUID id, UUID authenticatedUserId);

    Optional<Activity> findByConnection_IdAndConnection_User_IdAndDedupKey(
            UUID connectionId, UUID authenticatedUserId, String dedupKey);
}
