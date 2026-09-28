package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;

public interface ConnectionRepository extends JpaRepository<ConnectionEntity, UUID> {

    Optional<ConnectionEntity> findByIdAndUser_IdAndDeletedAtIsNull(UUID id, UUID authenticatedUserId);

    Optional<ConnectionEntity> findByIdAndUser_Id(UUID id, UUID authenticatedUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select connection from ConnectionEntity connection "
            + "where connection.id = :connectionId and connection.user.id = :userId "
            + "and connection.deletedAt is null")
    Optional<ConnectionEntity> findActiveByIdAndUserIdForUpdate(
            @Param("connectionId") UUID connectionId, @Param("userId") UUID userId);

    List<ConnectionEntity> findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID authenticatedUserId);

    boolean existsByUser_IdAndProviderAndDeletedAtIsNull(UUID authenticatedUserId, ConnectionProvider provider);

    boolean existsByUser_IdAndProviderAndExternalAccountRefAndDeletedAtIsNull(
            UUID authenticatedUserId, ConnectionProvider provider, String externalAccountRef);
}
