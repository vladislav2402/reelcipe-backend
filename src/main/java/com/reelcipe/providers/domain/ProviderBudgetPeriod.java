package com.reelcipe.providers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_budget_periods")
public class ProviderBudgetPeriod {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "budget_units", nullable = false)
    private long budgetUnits;

    @Column(name = "reserved_units", nullable = false)
    private long reservedUnits;

    @Column(name = "consumed_units", nullable = false)
    private long consumedUnits;

    @Column(name = "active_attempts", nullable = false)
    private int activeAttempts;

    @Column(name = "max_concurrent", nullable = false)
    private int maxConcurrent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderBudgetPeriod() {
    }

    public ProviderBudgetPeriod(
            UUID id,
            String provider,
            Instant periodStart,
            Instant periodEnd,
            long budgetUnits,
            int maxConcurrent,
            Instant now) {
        this.id = id;
        this.provider = provider;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.budgetUnits = budgetUnits;
        this.maxConcurrent = maxConcurrent;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void reserve(long units, Instant now) {
        reservedUnits += units;
        activeAttempts++;
        updatedAt = now;
    }

    public void consumeReservation(long reserved, long actual, Instant now) {
        reservedUnits -= reserved;
        consumedUnits += actual;
        activeAttempts = Math.max(0, activeAttempts - 1);
        updatedAt = now;
    }

    public void releaseReservation(long units, Instant now) {
        reservedUnits = Math.max(0, reservedUnits - units);
        activeAttempts = Math.max(0, activeAttempts - 1);
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public long getBudgetUnits() {
        return budgetUnits;
    }

    public long getReservedUnits() {
        return reservedUnits;
    }

    public long getConsumedUnits() {
        return consumedUnits;
    }

    public int getActiveAttempts() {
        return activeAttempts;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }
}
