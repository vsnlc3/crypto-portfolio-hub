package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncState;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStateId;

public interface ConnectionSyncStateRepository extends JpaRepository<ConnectionSyncState, ConnectionSyncStateId> {

    List<ConnectionSyncState> findAllByConnection_User_IdAndConnection_DeletedAtIsNull(UUID authenticatedUserId);

    Optional<ConnectionSyncState> findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
            ConnectionSyncStateId id, UUID authenticatedUserId);

    @Query("select state from ConnectionSyncState state where state.id.connectionId = :connectionId "
            + "and state.id.userId = :userId")
    List<ConnectionSyncState> findAllByConnectionAndUser(
            @Param("connectionId") UUID connectionId, @Param("userId") UUID userId);

    long deleteAllByConnection_IdAndConnection_User_Id(UUID connectionId, UUID authenticatedUserId);
}
