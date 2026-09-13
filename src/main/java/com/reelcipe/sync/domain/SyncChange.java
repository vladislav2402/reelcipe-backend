package com.reelcipe.sync.domain;

import java.time.Instant;
import java.util.UUID;

public record SyncChange(
        UUID id,
        UUID userId,
        String entityType,
        UUID entityId,
        String operation,
        long version,
        long sequence,
        Instant createdAt) {
}
