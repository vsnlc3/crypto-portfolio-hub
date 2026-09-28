package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cryptoportfoliohub.persistence.entity.SyncRunResult;
import com.cryptoportfoliohub.persistence.entity.SyncRunResultId;

public interface SyncRunResultRepository extends JpaRepository<SyncRunResult, SyncRunResultId> {

    List<SyncRunResult> findAllBySyncRun_Connection_User_Id(UUID authenticatedUserId);

    Optional<SyncRunResult> findByIdAndSyncRun_Connection_User_Id(
            SyncRunResultId id, UUID authenticatedUserId);

    @Query("select result from SyncRunResult result where result.syncRun.id = :syncRunId "
            + "and result.syncRun.connection.id = :connectionId "
            + "and result.syncRun.connection.user.id = :userId order by result.id.capability")
    List<SyncRunResult> findAllOwnedResults(
            @Param("syncRunId") UUID syncRunId,
            @Param("connectionId") UUID connectionId,
            @Param("userId") UUID authenticatedUserId);
}
