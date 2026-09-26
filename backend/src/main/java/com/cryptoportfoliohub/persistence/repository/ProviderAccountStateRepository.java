package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.ProviderAccountState;

public interface ProviderAccountStateRepository extends JpaRepository<ProviderAccountState, UUID> {

    List<ProviderAccountState> findAllByConnection_User_IdAndConnection_DeletedAtIsNull(UUID authenticatedUserId);

    Optional<ProviderAccountState> findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
            UUID id, UUID authenticatedUserId);

    long deleteAllByConnection_IdAndConnection_User_Id(UUID connectionId, UUID authenticatedUserId);
}
