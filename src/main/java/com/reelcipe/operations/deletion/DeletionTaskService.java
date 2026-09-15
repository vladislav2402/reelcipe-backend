package com.reelcipe.operations.deletion;

import com.reelcipe.operations.deletion.domain.DeletionTask;
import com.reelcipe.operations.deletion.domain.DeletionTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class DeletionTaskService {
    private final DeletionTaskRepository repository;
    private final Clock clock;

    public DeletionTaskService(DeletionTaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public UUID enqueue(
            UUID userId,
            String resourceType,
            String resourceKey,
            String deduplicationKey) {
        UUID existing = repository.findByDeduplicationKey(deduplicationKey)
                .map(DeletionTask::getId)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        UUID id = UUID.randomUUID();
        repository.insertIfAbsent(
                id,
                userId,
                resourceType,
                resourceKey,
                deduplicationKey,
                clock.instant());
        return repository.findByDeduplicationKey(deduplicationKey)
                .orElseThrow(() -> new IllegalStateException("Deletion task was not persisted"))
                .getId();
    }
}
