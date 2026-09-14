package com.reelcipe.imports;

import com.reelcipe.auth.FixtureUserInitializer;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.role=worker",
        "spring.profiles.active=test",
        "app.worker.enabled=false",
        "app.worker.lease-duration=PT2S"
})
@Testcontainers
class ImportLeaseIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired
    private ImportQueue queue;

    @Autowired
    private ImportLeasePersistence persistence;

    @Autowired
    private ImportJobRepository jobs;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void concurrentWorkersClaimDifferentJobs() throws Exception {
        saveQueuedJob();
        saveQueuedJob();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Optional<ImportLease>>> calls = List.of(
                    () -> queue.claimNext("worker-a"),
                    () -> queue.claimNext("worker-b"));
            List<Optional<ImportLease>> claims = executor.invokeAll(calls).stream()
                    .map(ImportLeaseIntegrationTest::get)
                    .toList();

            assertThat(claims).allMatch(Optional::isPresent);
            assertThat(claims.stream().map(Optional::get).map(ImportLease::importId).distinct())
                    .hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredLeaseIsReclaimedAndOldWorkerCannotCheckpoint() throws Exception {
        ImportJob job = saveQueuedJob();
        ImportLease first = queue.claimNext("worker-a").orElseThrow();
        persistence.checkpoint(first, "source-v1");

        Thread.sleep(2500);

        ImportLease second = queue.claimNext("worker-b").orElseThrow();
        assertThat(second.importId()).isEqualTo(job.getId());
        assertThat(second.leaseVersion()).isEqualTo(first.leaseVersion() + 1);
        assertThat(persistence.heartbeat(first)).isFalse();
        assertThatThrownBy(() -> persistence.checkpoint(first, "late-result"))
                .isInstanceOf(LeaseLostException.class);
    }

    private ImportJob saveQueuedJob() {
        Instant now = Instant.now();
        ImportJob job = new ImportJob(
                UUID.randomUUID(),
                FixtureUserInitializer.ALICE_ID,
                UUID.randomUUID(),
                ImportSourceType.LINK,
                "https://example.com/" + UUID.randomUUID(),
                "hash-v1",
                ImportStatus.QUEUED,
                ImportStage.RESOLVING,
                now.plusSeconds(300),
                now.plusSeconds(600),
                now);
        return jobs.saveAndFlush(job);
    }

    private static Optional<ImportLease> get(
            java.util.concurrent.Future<Optional<ImportLease>> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("Worker claim failed", exception);
        }
    }
}
