package com.reelcipe.operations.outbox.domain;

import java.util.UUID;

public class OutboxLeaseLostException extends RuntimeException {
    public OutboxLeaseLostException(UUID outboxId) {
        super("Outbox lease was lost: " + outboxId);
    }
}
