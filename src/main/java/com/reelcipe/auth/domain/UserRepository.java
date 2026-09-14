package com.reelcipe.auth.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.id = :id AND user.status = :status")
    Optional<User> findLockedByIdAndStatus(UUID id, UserStatus status);

    long countByIdAndStatus(UUID id, UserStatus status);

    Optional<User> findByIdAndStatus(UUID id, UserStatus status);

}
