package com.reelcipe.operations.outbox;

import com.reelcipe.operations.outbox.domain.OperationOutbox;

import java.util.UUID;

public record OutboxLease(
        UUID id,
        String owner,
        long version,
        OperationOutbox event) {
}
