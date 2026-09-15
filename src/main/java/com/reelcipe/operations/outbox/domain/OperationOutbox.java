package com.reelcipe.operations.outbox.domain;

import jakarta.persistence.*;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operation_outbox")
public class OperationOutbox {
    @Id
    private UUID id;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "deduplication_key", nullable = false)
    private String deduplicationKey;
    @Column(nullable = false)
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;
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
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected OperationOutbox() {
    }

    public OperationOutbox(
            UUID id,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            String deduplicationKey,
            String payload,
            Instant now) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.deduplicationKey = deduplicationKey;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void claim(String owner, Instant leaseUntil, Clock clock) {
        if (!claimable() || owner == null || owner.isBlank() || leaseUntil == null) {
            throw new IllegalStateException("Outbox event is not claimable");
        }
        status = OutboxStatus.PROCESSING;
        leaseOwner = owner;
        leaseVersion++;
        this.leaseUntil = leaseUntil;
        nextAttemptAt = null;
        updatedAt = clock.instant();
    }

    public void markDelivered(String owner, long version, Clock clock) {
        requireLease(owner, version, clock);
        status = OutboxStatus.DELIVERED;
        deliveredAt = clock.instant();
        updatedAt = deliveredAt;
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
            status = OutboxStatus.FAILED;
            nextAttemptAt = null;
        } else {
            status = OutboxStatus.RETRY_WAIT;
            nextAttemptAt = nextAttempt;
        }
    }

    private void requireLease(String owner, long version, Clock clock) {
        if (status != OutboxStatus.PROCESSING
                || !owner.equals(leaseOwner)
                || version != leaseVersion
                || leaseUntil == null
                || !leaseUntil.isAfter(clock.instant())) {
            throw new OutboxLeaseLostException(id);
        }
    }

    private boolean claimable() {
        return status == OutboxStatus.PENDING || status == OutboxStatus.RETRY_WAIT;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getDeduplicationKey() {
        return deduplicationKey;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
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

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
