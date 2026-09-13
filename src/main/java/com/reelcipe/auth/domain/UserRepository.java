package com.reelcipe.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    long countByIdAndStatus(UUID id, UserStatus status);

    Optional<User> findByIdAndStatus(UUID id, UserStatus status);

}
