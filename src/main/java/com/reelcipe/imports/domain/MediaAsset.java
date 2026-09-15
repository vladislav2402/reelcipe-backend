package com.reelcipe.imports.domain;

import jakarta.persistence.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
public class MediaAsset {
    @Id
    private UUID id;
    @Column(name = "import_id", nullable = false)
    private UUID importId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "upload_attempt_id")
    private UUID uploadAttemptId;
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private MediaAssetType assetType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaAssetStatus status;
    @Column(name = "staging_key")
    private String stagingKey;
    @Column(name = "processing_key")
    private String processingKey;
    @Column(name = "sha256")
    private String sha256;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "duration_seconds")
    private Integer durationSeconds;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaAsset() {
    }

    public MediaAsset(
            UUID id,
            UUID importId,
            UUID userId,
            UUID uploadAttemptId,
            MediaAssetType assetType,
            String stagingKey,
            long sizeBytes,
            String contentType,
            Instant expiresAt,
            Instant now) {
        this.id = id;
        this.importId = importId;
        this.userId = userId;
        this.uploadAttemptId = uploadAttemptId;
        this.assetType = assetType;
        this.status = MediaAssetStatus.ACCEPTED;
        this.stagingKey = stagingKey;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
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

    public UUID getUploadAttemptId() {
        return uploadAttemptId;
    }

    public MediaAssetType getAssetType() {
        return assetType;
    }

    public MediaAssetStatus getStatus() {
        return status;
    }

    public String getStagingKey() {
        return stagingKey;
    }

    public String getProcessingKey() {
        return processingKey;
    }

    public String getSha256() {
        return sha256;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void recordProcessingCopy(
            String processingKey,
            String sha256,
            long actualSizeBytes,
            Clock clock) {
        if (this.processingKey != null) {
            if (!this.processingKey.equals(processingKey)
                    || !Objects.equals(this.sha256, sha256)
                    || this.sizeBytes != actualSizeBytes) {
                throw new IllegalStateException("Processing copy is already immutable");
            }
            return;
        }
        if (this.sizeBytes != actualSizeBytes) {
            throw new IllegalArgumentException("Processing copy size does not match the source asset");
        }
        this.processingKey = processingKey;
        this.sha256 = sha256;
        this.status = MediaAssetStatus.READY;
        this.updatedAt = clock.instant();
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
