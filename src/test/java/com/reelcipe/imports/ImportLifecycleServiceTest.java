package com.reelcipe.imports;

import com.reelcipe.auth.domain.User;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.billing.QuotaService;
import com.reelcipe.imports.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportLifecycleServiceTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Mock
    private ImportJobRepository jobs;
    @Mock
    private UserRepository users;
    @Mock
    private QuotaService quota;
    @Mock
    private ImportService imports;

    private ImportLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new ImportLifecycleService(
                jobs,
                users,
                quota,
                imports,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(24),
                3,
                1000);
        when(users.findLockedByIdAndStatus(USER_ID, UserStatus.ACTIVE))
                .thenReturn(Optional.of(new User(USER_ID, "Test", UserStatus.ACTIVE, NOW, NOW)));
        lenient().when(quota.current(USER_ID)).thenReturn(snapshot());
    }

    @Test
    void cancelTransitionsJobAndReleasesReservation() {
        ImportJob job = job(ImportStatus.QUEUED);
        when(jobs.findLockedByIdAndUserId(job.getId(), USER_ID)).thenReturn(Optional.of(job));
        ImportService.ImportView view = null;
        when(imports.view(eq(job), any())).thenReturn(view);

        service.cancel(USER_ID, job.getId());

        assertThat(job.getStatus()).isEqualTo(ImportStatus.CANCELLED);
        verify(quota).releaseImport(USER_ID, job.getId());
    }

    @Test
    void retryRequiresFailedJob() {
        ImportJob job = job(ImportStatus.QUEUED);
        when(jobs.findLockedByIdAndUserId(job.getId(), USER_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.retry(USER_ID, job.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(409);
    }

    private QuotaService.QuotaSnapshot snapshot() {
        return new QuotaService.QuotaSnapshot(
                com.reelcipe.billing.domain.QuotaPlan.FREE,
                LocalDate.of(2026, 9, 1), 10, 1, 0, 9);
    }

    private ImportJob job(ImportStatus status) {
        return new ImportJob(
                UUID.randomUUID(),
                USER_ID,
                UUID.randomUUID(),
                ImportSourceType.LINK,
                "https://example.com/video",
                "hash-v1",
                status,
                ImportStage.RESOLVING,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(86400),
                NOW);
    }
}
