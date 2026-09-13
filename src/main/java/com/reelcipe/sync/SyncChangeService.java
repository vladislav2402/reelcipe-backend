package com.reelcipe.sync;

import com.reelcipe.sync.domain.SyncChange;
import com.reelcipe.sync.domain.SyncChangeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class SyncChangeService {

    private final SyncChangeRepository repository;

    public SyncChangeService(SyncChangeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SyncChange record(
            UUID userId,
            String entityType,
            UUID entityId,
            String operation,
            long version) {
        validate(userId, entityType, entityId, operation, version);
        long sequence = repository.lockAndIncrementSequence(userId);
        SyncChange change = new SyncChange(
                UUID.randomUUID(), userId, entityType, entityId, operation, version, sequence, Instant.now());
        repository.append(change);
        return change;
    }

    @Transactional(readOnly = true)
    public long captureWatermark(UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is required");
        }
        return repository.currentSequence(userId);
    }

    private void validate(UUID userId, String entityType, UUID entityId, String operation, long version) {
        if (userId == null || entityType == null || entityType.isBlank() || entityId == null
                || operation == null || operation.isBlank() || version < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sync change");
        }
    }
}
