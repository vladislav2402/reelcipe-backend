package com.reelcipe.auth.domain;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, UUID sessionId) {
}
