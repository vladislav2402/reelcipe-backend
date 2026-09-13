package com.reelcipe.idempotency.domain;

public record IdempotencyResult(int status, String body, String contentType) {
    public static IdempotencyResult ok(String body) {
        return new IdempotencyResult(200, body, "application/json");
    }

    public static IdempotencyResult created(String body) {
        return new IdempotencyResult(201, body, "application/json");
    }
}
