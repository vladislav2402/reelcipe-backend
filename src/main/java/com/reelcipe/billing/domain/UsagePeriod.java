package com.reelcipe.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "usage_periods")
public class UsagePeriod {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "quota_limit", nullable = false)
    private int limit;
    @Column(nullable = false)
    private int reserved;
    @Column(nullable = false)
    private int consumed;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UsagePeriod() {
    }

    public UsagePeriod(UUID id, UUID userId, LocalDate periodStart, int limit, Instant now) {
        this.id = id;
        this.userId = userId;
        this.periodStart = periodStart;
        this.limit = limit;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void applyPlanLimit(int planLimit, Instant now) {
        limit = Math.max(planLimit, consumed + reserved);
        updatedAt = now;
    }

    public void reserve(int units, Instant now) {
        if (units < 1 || consumed + reserved + units > limit) {
            throw new IllegalStateException("Quota limit exceeded");
        }
        reserved += units;
        updatedAt = now;
    }

    public void consume(int units, Instant now) {
        if (units < 1 || reserved < units) {
            throw new IllegalStateException("Quota reservation is inconsistent");
        }
        reserved -= units;
        consumed += units;
        updatedAt = now;
    }

    public void release(int units, Instant now) {
        if (units < 1 || reserved < units) {
            throw new IllegalStateException("Quota reservation is inconsistent");
        }
        reserved -= units;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public int getLimit() {
        return limit;
    }

    public int getReserved() {
        return reserved;
    }

    public int getConsumed() {
        return consumed;
    }

    public int getRemaining() {
        return Math.max(0, limit - consumed - reserved);
    }
}
