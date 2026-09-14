package com.reelcipe.billing.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "usage_ledger")
public class UsageLedger {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "import_id", nullable = false)
    private UUID importId;
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UsageOperation operation;
    @Column(nullable = false)
    private int units;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UsageLedger() {
    }

    public UsageLedger(UUID id, UUID userId, UUID importId, UUID reservationId, LocalDate periodStart,
                       UsageOperation operation, int units, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.importId = importId;
        this.reservationId = reservationId;
        this.periodStart = periodStart;
        this.operation = operation;
        this.units = units;
        this.createdAt = createdAt;
    }
}
