package com.reelcipe.idempotency.domain;

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

public interface IdempotencyRepository extends JpaRepository<IdempotencyRequest, UUID> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO idempotency_requests
                (id, user_id, operation, target, idempotency_key, request_hash, expires_at)
            VALUES (:id, :userId, :operation, :target, :idempotencyKey, :requestHash, :expiresAt)
            ON CONFLICT (user_id, operation, target, idempotency_key) DO UPDATE
            SET id = EXCLUDED.id, request_hash = EXCLUDED.request_hash,
                response_status = NULL, response_body = NULL, response_content_type = NULL,
                expires_at = EXCLUDED.expires_at, completed_at = NULL
            WHERE idempotency_requests.expires_at <= CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsertIfExpired(@Param("id") UUID id, @Param("userId") UUID userId,
            @Param("operation") String operation, @Param("target") String target,
            @Param("idempotencyKey") String idempotencyKey, @Param("requestHash") String requestHash,
            @Param("expiresAt") Instant expiresAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request FROM IdempotencyRequest request
            WHERE request.userId = :userId AND request.operation = :operation
              AND request.target = :target AND request.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRequest> findLockedByUserIdAndOperationAndTargetAndIdempotencyKey(
            @Param("userId") UUID userId, @Param("operation") String operation,
            @Param("target") String target, @Param("idempotencyKey") String idempotencyKey);

    @Modifying
    @Transactional
    @Query("""
            UPDATE IdempotencyRequest request
            SET request.responseStatus = :status, request.responseBody = :body,
                request.responseContentType = :contentType, request.completedAt = :completedAt
            WHERE request.id = :id
            """)
    void complete(@Param("id") UUID id, @Param("status") int status, @Param("body") String body,
            @Param("contentType") String contentType, @Param("completedAt") Instant completedAt);

}
