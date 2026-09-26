package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.SyncRunResult;
import com.cryptoportfoliohub.persistence.entity.SyncRunResultId;

public interface SyncRunResultRepository extends JpaRepository<SyncRunResult, SyncRunResultId> {

    List<SyncRunResult> findAllBySyncRun_Connection_User_Id(UUID authenticatedUserId);

    Optional<SyncRunResult> findByIdAndSyncRun_Connection_User_Id(
            SyncRunResultId id, UUID authenticatedUserId);
}
