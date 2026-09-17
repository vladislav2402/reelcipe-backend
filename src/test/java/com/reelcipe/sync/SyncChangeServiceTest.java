package com.reelcipe.sync;

import com.reelcipe.common.UuidV7;
import com.reelcipe.sync.domain.SyncChange;
import com.reelcipe.sync.domain.SyncChangeRepository;
import com.reelcipe.sync.domain.UserSyncState;
import com.reelcipe.sync.domain.UserSyncStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncChangeServiceTest {

    @Mock
    private SyncChangeRepository repository;

    @Mock
    private UserSyncStateRepository stateRepository;

    private SyncChangeService service;

    @BeforeEach
    void setUp() {
        service = new SyncChangeService(repository, stateRepository);
    }

    @Test
    void recordAllocatesSequenceBeforeAppendingChange() {
        UUID userId = UuidV7.randomUuid();
        UUID recipeId = UuidV7.randomUuid();
        when(stateRepository.findLocked(userId)).thenReturn(java.util.Optional.of(new UserSyncState(userId)));

        SyncChange result = service.record(userId, "recipe", recipeId, "UPDATE", 3);

        assertEquals(1L, result.sequence());
        assertEquals(3L, result.version());
        verify(repository).save(any(SyncChange.class));
    }

    @Test
    void recordRejectsInvalidVersion() {
        assertThrows(ResponseStatusException.class,
                () -> service.record(UuidV7.randomUuid(), "recipe", UuidV7.randomUuid(), "UPDATE", 0));
    }

    @Test
    void watermarkDelegatesToRepository() {
        UUID userId = UuidV7.randomUuid();
        when(stateRepository.findById(userId)).thenReturn(java.util.Optional.empty());

        assertEquals(0L, service.captureWatermark(userId));
    }
}
