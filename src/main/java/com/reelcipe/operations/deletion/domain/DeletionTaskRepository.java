package com.reelcipe.operations.deletion.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DeletionTaskRepository extends JpaRepository<DeletionTask, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO deletion_tasks (
                id, user_id, resource_type, resource_key, deduplication_key,
                status, attempts, next_attempt_at, created_at, updated_at)
            VALUES (
                :id, :userId, :resourceType, :resourceKey, :deduplicationKey,
                'PENDING', 0, :now, :now, :now)
            ON CONFLICT (deduplication_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("resourceType") String resourceType,
            @Param("resourceKey") String resourceKey,
            @Param("deduplicationKey") String deduplicationKey,
            @Param("now") java.time.Instant now);

    Optional<DeletionTask> findByDeduplicationKey(String deduplicationKey);

    @Query(value = """
            SELECT *
            FROM deletion_tasks
            WHERE status IN ('PENDING', 'RETRY_WAIT')
              AND next_attempt_at <= CURRENT_TIMESTAMP
                  + CAST(:clockSkew AS interval)
              AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP
                   + CAST(:clockSkew AS interval))
            ORDER BY next_attempt_at, created_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<DeletionTask> findNextClaimable(@Param("clockSkew") String clockSkew);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT task
            FROM DeletionTask task
            WHERE task.id = :id
              AND task.leaseOwner = :owner
              AND task.leaseVersion = :version
            """)
    Optional<DeletionTask> findLockedByLease(
            UUID id,
            String owner,
            long version);
}
