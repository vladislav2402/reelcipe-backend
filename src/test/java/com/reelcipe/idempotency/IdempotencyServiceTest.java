package com.reelcipe.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.common.UuidV7;
import com.reelcipe.idempotency.domain.IdempotencyRepository;
import com.reelcipe.idempotency.domain.IdempotencyRequest;
import com.reelcipe.idempotency.domain.IdempotencyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final UUID USER_ID = UuidV7.randomUuid();

    @Mock
    private IdempotencyRepository repository;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository, new ObjectMapper(), Duration.ofHours(24));
    }

    @Test
    void canonicalHashIgnoresJsonObjectFieldOrder() {
        assertEquals(
                service.canonicalBodyHash("{\"b\":2,\"a\":1}"),
                service.canonicalBodyHash("{\"a\":1,\"b\":2}"));
    }

    @Test
    void completedRequestReturnsStoredResponseWithoutExecutingCommand() {
        IdempotencyRequest stored = request("{\"a\":1}", new IdempotencyResult(201, "created", "application/json"));
        when(repository.findLockedByUserIdAndOperationAndTargetAndIdempotencyKey(
                any(), any(), any(), any())).thenReturn(java.util.Optional.of(stored));
        AtomicInteger executions = new AtomicInteger();

        IdempotencyResult result = service.execute(
                USER_ID, "recipe.create", "recipe", "key-1", "{\"a\":1}",
                () -> {
                    executions.incrementAndGet();
                    return new IdempotencyResult(500, "wrong", "text/plain");
                });

        assertEquals(new IdempotencyResult(201, "created", "application/json"), result);
        assertEquals(0, executions.get());
    }

    @Test
    void differentBodyWithSameScopeIsConflict() {
        when(repository.findLockedByUserIdAndOperationAndTargetAndIdempotencyKey(
                any(), any(), any(), any())).thenReturn(java.util.Optional.of(request("{\"a\":1}", null)));

        assertThrows(ResponseStatusException.class, () -> service.execute(
                USER_ID, "recipe.create", "recipe", "key-1", "{\"a\":2}",
                () -> new IdempotencyResult(201, "created", "application/json")));
    }

    @Test
    void newRequestExecutesAndStoresResult() {
        when(repository.findLockedByUserIdAndOperationAndTargetAndIdempotencyKey(
                any(), any(), any(), any())).thenReturn(java.util.Optional.of(request("{\"a\":1}", null)));
        IdempotencyResult expected = new IdempotencyResult(201, "created", "application/json");

        IdempotencyResult result = service.execute(
                USER_ID, "recipe.create", "recipe", "key-1", "{\"a\":1}", () -> expected);

        assertEquals(expected, result);
        verify(repository).complete(any(), org.mockito.ArgumentMatchers.eq(expected.status()),
                org.mockito.ArgumentMatchers.eq(expected.body()),
                org.mockito.ArgumentMatchers.eq(expected.contentType()), any());
    }

    private IdempotencyRequest request(String body, IdempotencyResult result) {
        return new IdempotencyRequest(
                UuidV7.randomUuid(), USER_ID, "recipe.create", "recipe", "key-1",
                service.canonicalBodyHash(body),
                result == null ? null : result.status(),
                result == null ? null : result.body(),
                result == null ? null : result.contentType(),
                Instant.now().plusSeconds(3600),
                result == null ? null : Instant.now());
    }
}
