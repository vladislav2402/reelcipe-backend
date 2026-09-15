package com.reelcipe.operations.deletion.domain;

import jakarta.persistence.*;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deletion_tasks")
public class DeletionTask {
    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "resource_type", nullable = false)
    private String resourceType;
    @Column(name = "resource_key", nullable = false)
    private String resourceKey;
    @Column(name = "deduplication_key", nullable = false)
    private String deduplicationKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeletionStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    @Column(name = "lease_owner")
    private String leaseOwner;
    @Column(name = "lease_version", nullable = false)
    private long leaseVersion;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "last_error")
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected DeletionTask() {
    }

    public DeletionTask(
            UUID id,
            UUID userId,
            String resourceType,
            String resourceKey,
            String deduplicationKey,
            Instant now) {
        this.id = id;
        this.userId = userId;
        this.resourceType = resourceType;
        this.resourceKey = resourceKey;
        this.deduplicationKey = deduplicationKey;
        this.status = DeletionStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void claim(String owner, Instant leaseUntil, Clock clock) {
        if (!claimable() || owner == null || owner.isBlank() || leaseUntil == null) {
            throw new IllegalStateException("Deletion task is not claimable");
        }
        status = DeletionStatus.PROCESSING;
        leaseOwner = owner;
        leaseVersion++;
        this.leaseUntil = leaseUntil;
        nextAttemptAt = null;
        updatedAt = clock.instant();
    }

    public void markCompleted(String owner, long version, Clock clock) {
        requireLease(owner, version, clock);
        status = DeletionStatus.COMPLETED;
        completedAt = clock.instant();
        updatedAt = completedAt;
        leaseOwner = null;
        leaseUntil = null;
    }

    public void scheduleRetry(
            String owner,
            long version,
            Instant nextAttempt,
            String error,
            int maxAttempts,
            Clock clock) {
        requireLease(owner, version, clock);
        attempts++;
        lastError = error;
        updatedAt = clock.instant();
        leaseOwner = null;
        leaseUntil = null;
        if (attempts >= maxAttempts) {
            status = DeletionStatus.FAILED;
            nextAttemptAt = null;
        } else {
            status = DeletionStatus.RETRY_WAIT;
            nextAttemptAt = nextAttempt;
        }
    }

    private void requireLease(String owner, long version, Clock clock) {
        if (status != DeletionStatus.PROCESSING
                || !owner.equals(leaseOwner)
                || version != leaseVersion
                || leaseUntil == null
                || !leaseUntil.isAfter(clock.instant())) {
            throw new DeletionLeaseLostException(id);
        }
    }

    private boolean claimable() {
        return status == DeletionStatus.PENDING || status == DeletionStatus.RETRY_WAIT;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceKey() {
        return resourceKey;
    }

    public String getDeduplicationKey() {
        return deduplicationKey;
    }

    public DeletionStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public long getLeaseVersion() {
        return leaseVersion;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public String getLastError() {
        return lastError;
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
