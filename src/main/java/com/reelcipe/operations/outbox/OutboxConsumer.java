package com.reelcipe.operations.outbox;

import com.reelcipe.operations.outbox.domain.OperationOutbox;

public interface OutboxConsumer {
    boolean supports(String eventType);

    void consume(OperationOutbox event);
}
