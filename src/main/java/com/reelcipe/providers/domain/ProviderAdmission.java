package com.reelcipe.providers.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_admissions")
public class ProviderAdmission {
    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false, unique = true)
    private UUID attemptId;

    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String operation;

    @Column(name = "estimated_units", nullable = false)
    private long estimatedUnits;

    @Column(name = "charged_units", nullable = false)
    private long chargedUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderAdmissionState state;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderAdmission() {
    }

    public ProviderAdmission(
            UUID id,
            UUID attemptId,
            UUID importId,
            UUID userId,
            String provider,
            String operation,
            long estimatedUnits,
            Instant expiresAt,
            Instant now) {
        this.id = id;
        this.attemptId = attemptId;
        this.importId = importId;
        this.userId = userId;
        this.provider = provider;
        this.operation = operation;
        this.estimatedUnits = estimatedUnits;
        this.expiresAt = expiresAt;
        this.state = ProviderAdmissionState.RESERVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void consume(long units, Instant now) {
        if (state != ProviderAdmissionState.RESERVED) {
            return;
        }
        state = ProviderAdmissionState.CONSUMED;
        chargedUnits = units;
        updatedAt = now;
    }

    public void unknown(String code, Instant now) {
        if (state != ProviderAdmissionState.RESERVED) {
            return;
        }
        state = ProviderAdmissionState.UNKNOWN;
        chargedUnits = estimatedUnits;
        failureCode = code;
        updatedAt = now;
    }

    public void release(Instant now) {
        if (state != ProviderAdmissionState.RESERVED) {
            return;
        }
        state = ProviderAdmissionState.RELEASED;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public long getEstimatedUnits() {
        return estimatedUnits;
    }

    public ProviderAdmissionState getState() {
        return state;
    }
}
