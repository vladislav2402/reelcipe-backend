package com.reelcipe.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record UserProfile(UUID id, String displayName, String status, Instant createdAt) {
}
