package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.SyncRun;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    List<SyncRun> findAllByConnection_User_IdOrderByStartedAtDescIdDesc(UUID authenticatedUserId);

    Optional<SyncRun> findByIdAndConnection_User_Id(UUID id, UUID authenticatedUserId);
}
