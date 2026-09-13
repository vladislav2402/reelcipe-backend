package com.reelcipe.idempotency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_requests")
public class IdempotencyRequest {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private String operation;
    @Column(nullable = false)
    private String target;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false)
    private String requestHash;
    @Column(name = "response_status")
    private Integer responseStatus;
    @Column(name = "response_body")
    private String responseBody;
    @Column(name = "response_content_type")
    private String responseContentType;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected IdempotencyRequest() {
    }

    public IdempotencyRequest(UUID id, UUID userId, String operation, String target, String idempotencyKey,
                              String requestHash, Integer responseStatus, String responseBody, String responseContentType,
                              Instant expiresAt, Instant completedAt) {
        this.id = id;
        this.userId = userId;
        this.operation = operation;
        this.target = target;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.responseContentType = responseContentType;
        this.expiresAt = expiresAt;
        this.completedAt = completedAt;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String operation() {
        return operation;
    }

    public String target() {
        return target;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestHash() {
        return requestHash;
    }

    public Integer responseStatus() {
        return responseStatus;
    }

    public String responseBody() {
        return responseBody;
    }

    public String responseContentType() {
        return responseContentType;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
