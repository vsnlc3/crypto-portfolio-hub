package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.PerpetualPosition;

public interface PerpetualPositionRepository extends JpaRepository<PerpetualPosition, UUID> {

    List<PerpetualPosition> findAllByConnection_User_IdAndConnection_DeletedAtIsNull(UUID authenticatedUserId);

    Optional<PerpetualPosition> findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
            UUID id, UUID authenticatedUserId);

    long deleteAllByConnection_IdAndConnection_User_Id(UUID connectionId, UUID authenticatedUserId);
}
