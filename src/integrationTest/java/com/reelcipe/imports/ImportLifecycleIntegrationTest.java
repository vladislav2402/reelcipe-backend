package com.reelcipe.imports;

import com.reelcipe.auth.domain.User;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.billing.QuotaService;
import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.role=worker",
        "spring.profiles.active=test",
        "app.worker.enabled=false",
        "app.worker.lease-duration=PT30S",
        "app.worker.clock-skew-tolerance=PT5S",
        "app.worker.retry-jitter-ratio=0"
})
@Testcontainers
class ImportLifecycleIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired
    private ImportService imports;

    @Autowired
    private ImportQueue queue;

    @Autowired
    private ImportRetryService retry;

    @Autowired
    private ImportLeasePersistence leases;

    @Autowired
    private ImportLifecycleService lifecycle;

    @Autowired
    private ImportExpiryService expiry;

    @Autowired
    private ImportJobRepository jobs;

    @Autowired
    private UserRepository users;

    @Autowired
    private QuotaService quota;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void transientFailureReleasesLeaseAndSchedulesRetry() {
        ImportService.ImportView created = createImport();
        ImportLease lease = claim(created, "worker-retry");

        retry.handle(lease, ImportFailure.transientError("TEMPORARY_RESOLVER"));

        ImportJob job = jobs.findById(created.id()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(ImportStatus.RETRY_WAIT);
        assertThat(job.getLeaseOwner()).isNull();
        assertThat(job.getStageAttempts()).isEqualTo(1);
        assertThat(job.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void permanentFailureReleasesQuotaExactlyOnce() {
        ImportService.ImportView created = createImport();
        ImportLease lease = claim(created, "worker-failure");

        retry.handle(lease, ImportFailure.permanent("PERMANENT_INVALID_MEDIA"));

        ImportJob job = jobs.findById(created.id()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(quota.current(currentUser).reserved()).isEqualTo(0);
        assertThat(quota.releaseImport(currentUser, created.id())).isFalse();
        assertThatThrownBy(() -> leases.checkpoint(lease, "late"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void cancelFencesLateCheckpointAndReleasesQuota() {
        ImportService.ImportView created = createImport();
        ImportLease lease = claim(created, "worker-cancel");

        lifecycle.cancel(currentUser, created.id());

        assertThat(jobs.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(ImportStatus.CANCELLED);
        assertThat(quota.current(currentUser).reserved()).isEqualTo(0);
        assertThatThrownBy(() -> leases.checkpoint(lease, "late"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void needsInputContinuationIncrementsRevisionWithoutSecondReservation() {
        ImportService.ImportView created = createImport();
        ImportLease lease = claim(created, "worker-input");
        retry.handle(lease, ImportFailure.needsInput("DESCRIPTION_REQUIRED"));

        lifecycle.continueWithDescription(
                currentUser,
                created.id(),
                "Add the ingredient amounts from the description.");

        ImportJob job = jobs.findById(created.id()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(ImportStatus.QUEUED);
        assertThat(job.getInputRevision()).isEqualTo(2);
        assertThat(job.getDescriptionText()).contains("ingredient amounts");
        assertThat(quota.current(currentUser).reserved()).isEqualTo(1);
    }

    @Test
    void expiredJobIsTerminalAndReleasesReservation() {
        Instant now = Instant.now();
        UUID userId = createUser();
        ImportJob job = new ImportJob(
                UuidV7.randomUuid(),
                userId,
                UuidV7.randomUuid(),
                ImportSourceType.LINK,
                "https://example.com/expired",
                "expired-hash",
                ImportStatus.QUEUED,
                ImportStage.RESOLVING,
                now.minusSeconds(10),
                null,
                now.minusSeconds(11));
        jobs.saveAndFlush(job);
        quota.reserve(userId, job.getId());

        expiry.expireDue();

        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(ImportStatus.EXPIRED);
        assertThat(quota.current(userId).reserved()).isEqualTo(0);
    }

    private UUID currentUser;

    private ImportService.ImportView createImport() {
        currentUser = createUser();
        return imports.create(
                currentUser,
                UuidV7.randomUuid().toString(),
                new ImportService.ImportCommand(
                        UuidV7.randomUuid(),
                        ImportSourceType.LINK,
                        "https://example.com/b15/" + UuidV7.randomUuid(),
                        null,
                        null,
                        null,
                        null,
                        "Initial description"));
    }

    private ImportLease claim(ImportService.ImportView created, String workerId) {
        return queue.claimNext(workerId).orElseThrow(() -> {
            ImportJob job = jobs.findById(created.id()).orElseThrow();
            return new AssertionError(
                            "Job was not claimable: status=" + job.getStatus()
                            + ", nextAttemptAt=" + job.getNextAttemptAt()
                            + ", leaseUntil=" + job.getLeaseUntil()
                            + ", processingDeadlineAt=" + job.getProcessingDeadlineAt());
        });
    }

    private UUID createUser() {
        UUID userId = UuidV7.randomUuid();
        Instant now = Instant.now();
        users.saveAndFlush(new User(userId, "B15 test", UserStatus.ACTIVE, now, now));
        return userId;
    }
}
