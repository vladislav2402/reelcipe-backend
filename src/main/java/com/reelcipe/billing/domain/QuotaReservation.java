package com.reelcipe.billing.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "quota_reservations")
public class QuotaReservation {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "import_id", nullable = false)
    private UUID importId;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(nullable = false)
    private int generation;
    @Column(nullable = false)
    private int units;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuotaReservationState state;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected QuotaReservation() {
    }

    public QuotaReservation(UUID id, UUID userId, UUID importId, LocalDate periodStart, int generation,
                            int units, Instant now) {
        this.id = id;
        this.userId = userId;
        this.importId = importId;
        this.periodStart = periodStart;
        this.generation = generation;
        this.units = units;
        this.state = QuotaReservationState.RESERVED;
        this.createdAt = now;
    }

    public void consume(Instant now) {
        ensureReserved();
        state = QuotaReservationState.CONSUMED;
        completedAt = now;
    }

    public void release(Instant now) {
        ensureReserved();
        state = QuotaReservationState.RELEASED;
        completedAt = now;
    }

    private void ensureReserved() {
        if (state != QuotaReservationState.RESERVED) {
            throw new IllegalStateException("Quota reservation is already completed");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getImportId() {
        return importId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public int getGeneration() {
        return generation;
    }

    public int getUnits() {
        return units;
    }

    public QuotaReservationState getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
