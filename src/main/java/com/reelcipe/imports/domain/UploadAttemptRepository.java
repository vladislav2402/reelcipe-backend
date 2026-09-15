package com.reelcipe.imports.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadAttemptRepository extends JpaRepository<UploadAttempt, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT attempt
            FROM UploadAttempt attempt
            WHERE attempt.importId = :importId
              AND attempt.userId = :userId
            ORDER BY attempt.attemptNumber DESC
            """)
    List<UploadAttempt> findLatestLocked(
            @Param("importId") UUID importId,
            @Param("userId") UUID userId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UploadAttempt> findLockedByIdAndImportIdAndUserId(
            UUID id,
            UUID importId,
            UUID userId);

    List<UploadAttempt> findByStatusAndExpiresAtAfterOrderByCreatedAtAsc(
            UploadAttemptStatus status,
            Instant expiresAt,
            Pageable pageable);
}
