package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;

public interface ConnectionRepository extends JpaRepository<ConnectionEntity, UUID> {

    Optional<ConnectionEntity> findByIdAndUser_IdAndDeletedAtIsNull(UUID id, UUID authenticatedUserId);

    List<ConnectionEntity> findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID authenticatedUserId);

    boolean existsByUser_IdAndProviderAndDeletedAtIsNull(UUID authenticatedUserId, ConnectionProvider provider);

    boolean existsByUser_IdAndProviderAndExternalAccountRefAndDeletedAtIsNull(
            UUID authenticatedUserId, ConnectionProvider provider, String externalAccountRef);
}
