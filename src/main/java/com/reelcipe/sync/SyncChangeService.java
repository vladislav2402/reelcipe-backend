package com.reelcipe.sync;

import com.reelcipe.common.UuidV7;
import com.reelcipe.sync.domain.SyncChange;
import com.reelcipe.sync.domain.SyncChangeRepository;
import com.reelcipe.sync.domain.UserSyncState;
import com.reelcipe.sync.domain.UserSyncStateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class SyncChangeService {

    private final SyncChangeRepository repository;
    private final UserSyncStateRepository stateRepository;

    public SyncChangeService(SyncChangeRepository repository, UserSyncStateRepository stateRepository) {
        this.repository = repository;
        this.stateRepository = stateRepository;
    }

    @Transactional
    public SyncChange record(
            UUID userId,
            String entityType,
            UUID entityId,
            String operation,
            long version) {
        validate(userId, entityType, entityId, operation, version);
        stateRepository.ensureExists(userId);
        UserSyncState state = stateRepository.findLocked(userId).orElseThrow();
        long sequence = state.increment();
        stateRepository.save(state);
        SyncChange change = new SyncChange(
                UuidV7.randomUuid(), userId, entityType, entityId, operation, version, sequence, Instant.now());
        repository.save(change);
        return change;
    }

    @Transactional(readOnly = true)
    public long captureWatermark(UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is required");
        }
        return stateRepository.findById(userId).map(UserSyncState::lastSequence).orElse(0L);
    }

    private void validate(UUID userId, String entityType, UUID entityId, String operation, long version) {
        if (userId == null || entityType == null || entityType.isBlank() || entityId == null
                || operation == null || operation.isBlank() || version < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sync change");
        }
    }
}
