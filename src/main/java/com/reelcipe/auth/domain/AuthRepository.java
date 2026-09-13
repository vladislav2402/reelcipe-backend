package com.reelcipe.auth.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthRepository extends JpaRepository<UserSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM UserSession session WHERE session.refreshTokenHash = :refreshTokenHash")
    Optional<UserSession> findLockedByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Transactional
    @Query("""
            UPDATE UserSession session SET session.revokedAt = :now
            WHERE session.id = :id AND session.revokedAt IS NULL
            """)
    void revokeSession(@Param("id") UUID sessionId, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("""
            UPDATE UserSession session SET session.revokedAt = :now
            WHERE session.familyId = :familyId AND session.revokedAt IS NULL
            """)
    void revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
