package com.reelcipe.idempotency.domain;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
public class IdempotencyRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final IdempotencyRequestRowMapper rowMapper;

    public IdempotencyRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            IdempotencyRequestRowMapper rowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = rowMapper;
    }

    public IdempotencyRequest lockOrCreate(IdempotencyRequest request) {
        jdbcTemplate.update("""
                INSERT INTO idempotency_requests
                    (id, user_id, operation, target, idempotency_key, request_hash, expires_at)
                VALUES (:id, :userId, :operation, :target, :idempotencyKey, :requestHash, :expiresAt)
                ON CONFLICT (user_id, operation, target, idempotency_key) DO UPDATE
                SET id = EXCLUDED.id,
                    request_hash = EXCLUDED.request_hash,
                    response_status = NULL,
                    response_body = NULL,
                    response_content_type = NULL,
                    expires_at = EXCLUDED.expires_at,
                    completed_at = NULL
                WHERE idempotency_requests.expires_at <= CURRENT_TIMESTAMP
                """, new MapSqlParameterSource()
                .addValue("id", request.id())
                .addValue("userId", request.userId())
                .addValue("operation", request.operation())
                .addValue("target", request.target())
                .addValue("idempotencyKey", request.idempotencyKey())
                .addValue("requestHash", request.requestHash())
                .addValue("expiresAt", Timestamp.from(request.expiresAt())));

        return jdbcTemplate.query("""
                        SELECT id, user_id, operation, target, idempotency_key, request_hash,
                               response_status, response_body, response_content_type,
                               expires_at, completed_at
                        FROM idempotency_requests
                        WHERE user_id = :userId
                          AND operation = :operation
                          AND target = :target
                          AND idempotency_key = :idempotencyKey
                        FOR UPDATE
                        """,
                Map.of(
                        "userId", request.userId(),
                        "operation", request.operation(),
                        "target", request.target(),
                        "idempotencyKey", request.idempotencyKey()), rowMapper)
                .stream()
                .findFirst()
                .orElseThrow();
    }

    public void complete(UUID requestId, IdempotencyResult result, Instant completedAt) {
        jdbcTemplate.update("""
                UPDATE idempotency_requests
                SET response_status = :responseStatus,
                    response_body = :responseBody,
                    response_content_type = :responseContentType,
                    completed_at = :completedAt
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", requestId)
                .addValue("responseStatus", result.status())
                .addValue("responseBody", result.body())
                .addValue("responseContentType", result.contentType())
                .addValue("completedAt", Timestamp.from(completedAt)));
    }
}
