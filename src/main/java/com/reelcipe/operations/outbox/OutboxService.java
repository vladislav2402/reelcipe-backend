package com.reelcipe.operations.outbox;

import com.reelcipe.operations.outbox.domain.OperationOutbox;
import com.reelcipe.operations.outbox.domain.OperationOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class OutboxService {
    private final OperationOutboxRepository repository;
    private final Clock clock;

    public OutboxService(OperationOutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public UUID enqueue(
            String eventType,
            String aggregateType,
            UUID aggregateId,
            String deduplicationKey,
            String payload) {
        UUID existing = repository.findByEventTypeAndDeduplicationKey(eventType, deduplicationKey)
                .map(OperationOutbox::getId)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        UUID id = UUID.randomUUID();
        repository.insertIfAbsent(
                id,
                eventType,
                aggregateType,
                aggregateId,
                deduplicationKey,
                payload,
                clock.instant());
        return repository.findByEventTypeAndDeduplicationKey(eventType, deduplicationKey)
                .orElseThrow(() -> new IllegalStateException("Outbox event was not persisted"))
                .getId();
    }
}
