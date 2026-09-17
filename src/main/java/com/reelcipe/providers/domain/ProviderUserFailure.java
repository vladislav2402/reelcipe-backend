package com.reelcipe.providers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_user_failures")
public class ProviderUserFailure {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderUserFailure() {
    }

    public ProviderUserFailure(
            UUID id,
            UUID userId,
            String provider,
            Instant windowStart,
            Instant now) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.windowStart = windowStart;
        this.updatedAt = now;
    }

    public void record(int maxFailures, Instant blockedUntil, Instant now) {
        failureCount++;
        if (failureCount >= maxFailures) {
            this.blockedUntil = blockedUntil;
        }
        updatedAt = now;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public Instant getBlockedUntil() {
        return blockedUntil;
    }
}
