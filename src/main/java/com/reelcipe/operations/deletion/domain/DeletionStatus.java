package com.reelcipe.operations.deletion.domain;

public enum DeletionStatus {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    COMPLETED,
    FAILED
}
