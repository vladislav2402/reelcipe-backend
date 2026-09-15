package com.reelcipe.imports.domain;

import jakarta.persistence.*;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_attempts")
public class UploadAttempt {
    @Id
    private UUID id;
    @Column(name = "import_id", nullable = false)
    private UUID importId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;
    @Column(name = "staging_key", nullable = false)
    private String stagingKey;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "url_expires_at", nullable = false)
    private Instant urlExpiresAt;
    @Column(name = "expected_size_bytes", nullable = false)
    private long expectedSizeBytes;
    @Column(name = "expected_content_type", nullable = false)
    private String expectedContentType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadAttemptStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected UploadAttempt() {
    }

    public UploadAttempt(
            UUID id,
            UUID importId,
            UUID userId,
            int attemptNumber,
            String stagingKey,
            Instant expiresAt,
            Instant urlExpiresAt,
            long expectedSizeBytes,
            String expectedContentType,
            Instant now) {
        this.id = id;
        this.importId = importId;
        this.userId = userId;
        this.attemptNumber = attemptNumber;
        this.stagingKey = stagingKey;
        this.expiresAt = expiresAt;
        this.urlExpiresAt = urlExpiresAt;
        this.expectedSizeBytes = expectedSizeBytes;
        this.expectedContentType = expectedContentType;
        this.status = UploadAttemptStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isActiveAt(Instant now) {
        return status == UploadAttemptStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public boolean hasActiveUrlAt(Instant now) {
        return isActiveAt(now) && urlExpiresAt.isAfter(now);
    }

    public void supersede(Clock clock) {
        if (status == UploadAttemptStatus.ACTIVE) {
            status = UploadAttemptStatus.SUPERSEDED;
            updatedAt = clock.instant();
        }
    }

    public void expire(Clock clock) {
        if (status == UploadAttemptStatus.ACTIVE && !expiresAt.isAfter(clock.instant())) {
            status = UploadAttemptStatus.EXPIRED;
            updatedAt = clock.instant();
        }
    }

    public void complete(Clock clock) {
        if (status != UploadAttemptStatus.ACTIVE || !expiresAt.isAfter(clock.instant())) {
            throw new IllegalStateException("Upload attempt is no longer active");
        }
        status = UploadAttemptStatus.COMPLETED;
        completedAt = clock.instant();
        updatedAt = completedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getImportId() {
        return importId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getStagingKey() {
        return stagingKey;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUrlExpiresAt() {
        return urlExpiresAt;
    }

    public long getExpectedSizeBytes() {
        return expectedSizeBytes;
    }

    public String getExpectedContentType() {
        return expectedContentType;
    }

    public UploadAttemptStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
