package com.reelcipe.imports.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM ImportJob job WHERE job.id = :id AND job.userId = :userId")
    Optional<ImportJob> findLockedByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
            SELECT *
            FROM import_jobs
            WHERE processing_deadline_at > CURRENT_TIMESTAMP
              AND (
                    (status IN ('QUEUED', 'RETRY_WAIT')
                         AND next_attempt_at <= CURRENT_TIMESTAMP
                             + CAST(:clockSkew AS interval)
                     AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP
                          + CAST(:clockSkew AS interval)))
                    OR
                    (status IN ('RESOLVING', 'EXTRACTING_AUDIO', 'TRANSCRIBING',
                                'EXTRACTING_RECIPE', 'VALIDATING')
                     AND lease_until <= CURRENT_TIMESTAMP
                         + CAST(:clockSkew AS interval))
                  )
            ORDER BY next_attempt_at NULLS FIRST, created_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ImportJob> findNextClaimable(@Param("clockSkew") String clockSkew);

    @Modifying
    @Query(value = """
            UPDATE import_jobs
            SET lease_until = clock_timestamp() + CAST(:leaseDuration AS interval),
                updated_at = clock_timestamp()
            WHERE id = :id
              AND lease_owner = :owner
              AND lease_version = :leaseVersion
              AND input_revision = :inputRevision
              AND lease_until > clock_timestamp()
              AND processing_deadline_at > clock_timestamp()
              AND status IN ('RESOLVING', 'EXTRACTING_AUDIO', 'TRANSCRIBING',
                             'EXTRACTING_RECIPE', 'VALIDATING')
            """, nativeQuery = true)
    int heartbeat(
            @Param("id") UUID id,
            @Param("owner") String owner,
            @Param("leaseVersion") long leaseVersion,
            @Param("inputRevision") long inputRevision,
            @Param("leaseDuration") String leaseDuration);

    @Query(value = """
            SELECT *
            FROM import_jobs
            WHERE id = :id
              AND lease_owner = :owner
              AND lease_version = :leaseVersion
              AND input_revision = :inputRevision
              AND lease_until > clock_timestamp()
              AND processing_deadline_at > clock_timestamp()
              AND status IN ('RESOLVING', 'EXTRACTING_AUDIO', 'TRANSCRIBING',
                             'EXTRACTING_RECIPE', 'VALIDATING')
            FOR UPDATE
            """, nativeQuery = true)
    Optional<ImportJob> findFencedForUpdate(
            @Param("id") UUID id,
            @Param("owner") String owner,
            @Param("leaseVersion") long leaseVersion,
            @Param("inputRevision") long inputRevision);

    Optional<ImportJob> findByIdAndUserId(UUID id, UUID userId);

    Optional<ImportJob> findByUserIdAndClientRequestId(UUID userId, UUID clientRequestId);

    List<ImportJob> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

    long countByUserIdAndStatusIn(UUID userId, Set<ImportStatus> statuses);

    @Query(value = """
            SELECT id
            FROM import_jobs
            WHERE (
                    status IN ('AWAITING_UPLOAD', 'NEEDS_INPUT')
                    AND input_deadline_at <= CURRENT_TIMESTAMP
                  )
               OR (
                    status IN ('QUEUED', 'RESOLVING', 'EXTRACTING_AUDIO', 'TRANSCRIBING',
                               'EXTRACTING_RECIPE', 'VALIDATING', 'RETRY_WAIT')
                    AND processing_deadline_at <= CURRENT_TIMESTAMP
                  )
            ORDER BY updated_at, id
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findNextExpiredId();
}
