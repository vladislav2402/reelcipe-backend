package com.reelcipe.sync;

import com.reelcipe.sync.domain.SyncChange;
import com.reelcipe.sync.domain.SyncChangeRepository;
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

    private SyncChangeService service;

    @BeforeEach
    void setUp() {
        service = new SyncChangeService(repository);
    }

    @Test
    void recordAllocatesSequenceBeforeAppendingChange() {
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        when(repository.lockAndIncrementSequence(userId)).thenReturn(7L);

        SyncChange result = service.record(userId, "recipe", recipeId, "UPDATE", 3);

        assertEquals(7L, result.sequence());
        assertEquals(3L, result.version());
        verify(repository).append(any(SyncChange.class));
    }

    @Test
    void recordRejectsInvalidVersion() {
        assertThrows(ResponseStatusException.class,
                () -> service.record(UUID.randomUUID(), "recipe", UUID.randomUUID(), "UPDATE", 0));
    }

    @Test
    void watermarkDelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(repository.currentSequence(userId)).thenReturn(4L);

        assertEquals(4L, service.captureWatermark(userId));
    }
}
