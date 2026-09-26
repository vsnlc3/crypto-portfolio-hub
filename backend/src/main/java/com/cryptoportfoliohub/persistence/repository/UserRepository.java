package com.cryptoportfoliohub.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cryptoportfoliohub.persistence.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByGoogleSubject(String googleSubject);
}
