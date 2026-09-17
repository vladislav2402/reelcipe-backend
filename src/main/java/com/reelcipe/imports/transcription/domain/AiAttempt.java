package com.reelcipe.imports.transcription.domain;

import jakarta.persistence.*;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_attempts")
public class AiAttempt {
    @Id
    private UUID id;
    @Column(name = "import_id", nullable = false)
    private UUID importId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiAttemptKind kind;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiAttemptStatus status;
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;
    @Column(name = "input_hash", nullable = false)
    private String inputHash;
    @Column(nullable = false)
    private String provider;
    @Column(nullable = false)
    private String model;
    @Column(name = "provider_admission_id")
    private UUID providerAdmissionId;
    @Column(name = "provider_request_id")
    private String providerRequestId;
    @Column(name = "error_code")
    private String errorCode;
    @Column(name = "usage_seconds")
    private Integer usageSeconds;
    @Column(name = "usage_units")
    private Integer usageUnits;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiAttempt() {
    }

    public AiAttempt(
            UUID id,
            UUID importId,
            UUID userId,
            AiAttemptKind kind,
            int attemptNumber,
            String inputHash,
            String provider,
            String model,
            UUID providerAdmissionId,
            Instant now) {
        this.id = id;
        this.importId = importId;
        this.userId = userId;
        this.kind = kind;
        this.status = AiAttemptStatus.STARTED;
        this.attemptNumber = attemptNumber;
        this.inputHash = inputHash;
        this.provider = provider;
        this.model = model;
        this.providerAdmissionId = providerAdmissionId;
        this.startedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public AiAttempt(
            UUID id,
            UUID importId,
            UUID userId,
            AiAttemptKind kind,
            int attemptNumber,
            String inputHash,
            String provider,
            String model,
            Instant now) {
        this(id, importId, userId, kind, attemptNumber, inputHash, provider, model, null, now);
    }

    public void succeed(int usageSeconds, int usageUnits, Clock clock) {
        requireStarted();
        status = AiAttemptStatus.SUCCEEDED;
        this.usageSeconds = usageSeconds;
        this.usageUnits = usageUnits;
        completedAt = clock.instant();
        updatedAt = completedAt;
    }

    public void setProviderRequestId(String providerRequestId, Clock clock) {
        this.providerRequestId = providerRequestId;
        updatedAt = clock.instant();
    }

    public void markUnknown(String errorCode, Clock clock) {
        if (status != AiAttemptStatus.STARTED) {
            return;
        }
        status = AiAttemptStatus.UNKNOWN;
        this.errorCode = errorCode;
        completedAt = clock.instant();
        updatedAt = completedAt;
    }

    public void markStale(Clock clock) {
        if (status != AiAttemptStatus.STARTED) {
            return;
        }
        status = AiAttemptStatus.STALE;
        completedAt = clock.instant();
        updatedAt = completedAt;
    }

    private void requireStarted() {
        if (status != AiAttemptStatus.STARTED) {
            throw new IllegalStateException("AI attempt is not active");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getImportId() {
        return importId;
    }

    public AiAttemptStatus getStatus() {
        return status;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getInputHash() {
        return inputHash;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public UUID getProviderAdmissionId() {
        return providerAdmissionId;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Integer getUsageSeconds() {
        return usageSeconds;
    }

    public Integer getUsageUnits() {
        return usageUnits;
    }
}
