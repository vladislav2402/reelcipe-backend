package com.reelcipe.idempotency.domain;

import java.time.Instant;
import java.util.UUID;

public record IdempotencyRequest(
        UUID id,
        UUID userId,
        String operation,
        String target,
        String idempotencyKey,
        String requestHash,
        Integer responseStatus,
        String responseBody,
        String responseContentType,
        Instant expiresAt,
        Instant completedAt) {

    public boolean isCompleted() {
        return completedAt != null;
    }
}
