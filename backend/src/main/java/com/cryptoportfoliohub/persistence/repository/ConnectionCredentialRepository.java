package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.ConnectionCredential;

public interface ConnectionCredentialRepository extends JpaRepository<ConnectionCredential, UUID> {

    List<ConnectionCredential> findAllByConnection_IdAndConnection_User_IdAndConnection_DeletedAtIsNull(
            UUID connectionId, UUID authenticatedUserId);

    Optional<ConnectionCredential> findByIdAndConnection_User_IdAndConnection_DeletedAtIsNull(
            UUID id, UUID authenticatedUserId);

    long deleteAllByConnection_IdAndConnection_User_Id(UUID connectionId, UUID authenticatedUserId);
}
