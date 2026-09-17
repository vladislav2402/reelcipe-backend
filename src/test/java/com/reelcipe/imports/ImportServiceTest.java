package com.reelcipe.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reelcipe.auth.domain.User;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.billing.QuotaService;
import com.reelcipe.common.UuidV7;
import com.reelcipe.idempotency.IdempotencyService;
import com.reelcipe.imports.domain.ImportJob;
import com.reelcipe.imports.domain.ImportJobRepository;
import com.reelcipe.imports.domain.ImportSourceType;
import com.reelcipe.imports.domain.ImportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportServiceTest {
    private static final UUID USER_ID = UuidV7.randomUuid();
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

    @Mock
    private ImportJobRepository jobs;

    @Mock
    private UserRepository users;

    @Mock
    private IdempotencyService idempotency;

    @Mock
    private QuotaService quota;

    @Mock
    private UploadService uploads;

    private ImportService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new ImportService(
                jobs,
                users,
                idempotency,
                quota,
                uploads,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                2,
                200,
                1000,
                500,
                java.time.Duration.ofHours(24),
                java.time.Duration.ofHours(48));
        when(users.findLockedByIdAndStatus(USER_ID, UserStatus.ACTIVE))
                .thenReturn(Optional.of(new User(USER_ID, "Test", UserStatus.ACTIVE, NOW, NOW)));
        when(jobs.findByUserIdAndClientRequestId(eq(USER_ID), any(UUID.class)))
                .thenReturn(Optional.empty());
        when(jobs.countByUserIdAndStatusIn(eq(USER_ID), any()))
                .thenReturn(0L);
        when(quota.current(USER_ID)).thenReturn(snapshot(10, 9));
        when(jobs.save(any(ImportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotency.execute(
                eq(USER_ID), eq("import.create"), eq("imports"), eq("key"), any(String.class), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<com.reelcipe.idempotency.domain.IdempotencyResult> command =
                            invocation.getArgument(5);
                    return command.get();
                });
    }

    @Test
    void createsQueuedLinkAndReturnsQuota() {
        ImportService.ImportCommand command = new ImportService.ImportCommand(
                UuidV7.randomUuid(), ImportSourceType.LINK, "https://example.com/video",
                null, null, null, null, "Dinner");

        ImportService.ImportView result = service.create(USER_ID, "key", command);

        assertThat(result.status()).isEqualTo(ImportStatus.QUEUED);
        assertThat(result.pollAfterSeconds()).isEqualTo(2);
        assertThat(result.quota().remaining()).isEqualTo(9);
    }

    @Test
    void rejectsUploadWithoutRequiredMetadata() {
        ImportService.ImportCommand command = new ImportService.ImportCommand(
                UuidV7.randomUuid(), ImportSourceType.UPLOAD, null,
                null, "video.mp4", "video/mp4", 100L, null);

        assertThatThrownBy(() -> service.create(USER_ID, "key", command))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(400);
    }

    private QuotaService.QuotaSnapshot snapshot(int limit, int remaining) {
        return new QuotaService.QuotaSnapshot(
                com.reelcipe.billing.domain.QuotaPlan.FREE,
                LocalDate.of(2026, 9, 1), limit, limit - remaining, 0, remaining);
    }
}
