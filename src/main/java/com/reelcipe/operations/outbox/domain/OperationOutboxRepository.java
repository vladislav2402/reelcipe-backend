package com.reelcipe.operations.outbox.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OperationOutboxRepository extends JpaRepository<OperationOutbox, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO operation_outbox (
                id, event_type, aggregate_type, aggregate_id, deduplication_key,
                payload, status, attempts, next_attempt_at, created_at, updated_at)
            VALUES (
                :id, :eventType, :aggregateType, :aggregateId, :deduplicationKey,
                :payload, 'PENDING', 0, :now, :now, :now)
            ON CONFLICT (event_type, deduplication_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("eventType") String eventType,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") UUID aggregateId,
            @Param("deduplicationKey") String deduplicationKey,
            @Param("payload") String payload,
            @Param("now") java.time.Instant now);

    Optional<OperationOutbox> findByEventTypeAndDeduplicationKey(
            String eventType,
            String deduplicationKey);

    @Query(value = """
            SELECT *
            FROM operation_outbox
            WHERE status IN ('PENDING', 'RETRY_WAIT')
              AND next_attempt_at <= CURRENT_TIMESTAMP
                  + CAST(:clockSkew AS interval)
              AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP
                   + CAST(:clockSkew AS interval))
            ORDER BY next_attempt_at, created_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OperationOutbox> findNextClaimable(@Param("clockSkew") String clockSkew);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM OperationOutbox event
            WHERE event.id = :id
              AND event.leaseOwner = :owner
              AND event.leaseVersion = :version
            """)
    Optional<OperationOutbox> findLockedByLease(
            UUID id,
            String owner,
            long version);
}
