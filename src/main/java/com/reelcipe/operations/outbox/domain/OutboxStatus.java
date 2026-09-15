package com.reelcipe.operations.outbox.domain;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    DELIVERED,
    FAILED
}
