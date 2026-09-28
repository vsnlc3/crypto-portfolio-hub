package com.cryptoportfoliohub.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.PortfolioSnapshot;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {

    List<PortfolioSnapshot> findAllByUser_IdOrderBySnapshotAtDescIdDesc(UUID authenticatedUserId);

    Optional<PortfolioSnapshot> findFirstByUser_IdOrderBySnapshotAtDescIdDesc(UUID authenticatedUserId);

    Optional<PortfolioSnapshot> findByIdAndUser_Id(UUID id, UUID authenticatedUserId);
}
