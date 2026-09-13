package com.reelcipe.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record UserSession(
        UUID id,
        UUID userId,
        UUID familyId,
        String refreshTokenHash,
        Instant expiresAt,
        Instant revokedAt) {

    public UserSession(UUID userId, UUID familyId, String refreshTokenHash, Instant expiresAt) {
        this(UUID.randomUUID(), userId, familyId, refreshTokenHash, expiresAt, null);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
