package com.reelcipe.idempotency.domain;

public record IdempotencyResult(int status, String body, String contentType) {
}
