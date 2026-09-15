package com.reelcipe.imports;

import com.reelcipe.auth.domain.User;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.imports.domain.*;
import com.reelcipe.storage.InMemoryObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.role=worker",
        "app.worker.enabled=false",
        "spring.profiles.active=test",
        "app.storage.provider=memory"
})
@Testcontainers
class ProcessingCopyIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired
    private ImportService imports;

    @Autowired
    private UploadService uploads;

    @Autowired
    private ImportQueue queue;

    @Autowired
    private ProcessingCopyService copies;

    @Autowired
    private ImportJobRepository jobs;

    @Autowired
    private MediaAssetRepository assets;

    @Autowired
    private UploadAttemptRepository attempts;

    @Autowired
    private UserRepository users;

    @Autowired
    private InMemoryObjectStorage storage;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void processingCopyIsImmutableWhenStagingIsRewritten() {
        UUID userId = createUser();
        byte[] original = "original-source".getBytes(StandardCharsets.UTF_8);
        ImportService.ImportView created = createUpload(userId, original.length);
        UploadService.UploadUrlResponse url = uploads.getUploadUrl(userId, created.id());
        var attempt = attempts.findById(url.uploadAttemptId()).orElseThrow();
        String stagingKey = attempt.getStagingKey();
        storage.putBytes(stagingKey, original, "video/mp4");
        uploads.complete(userId, created.id(), url.uploadAttemptId());
        MediaAsset source = sourceAsset(url.uploadAttemptId());

        ImportLease lease = queue.claimNext("copy-worker").orElseThrow();
        copies.copy(lease, new ImportLeaseControl());
        MediaAsset committed = sourceAsset(url.uploadAttemptId());
        byte[] processingBytes = storage.bytes(committed.getProcessingKey());

        storage.putBytes(
                source.getStagingKey(),
                "rewritten-source".getBytes(StandardCharsets.UTF_8),
                "video/mp4");
        copies.copy(lease, new ImportLeaseControl());

        assertThat(committed.getProcessingKey()).startsWith("processing/");
        assertThat(committed.getSha256()).isNotBlank();
        assertThat(processingBytes).containsExactly(original);
        assertThat(storage.bytes(committed.getProcessingKey())).containsExactly(original);
    }

    @Test
    void staleLeaseCannotCommitProcessingReference() {
        UUID userId = createUser();
        byte[] original = "stale-source".getBytes(StandardCharsets.UTF_8);
        ImportService.ImportView created = createUpload(userId, original.length);
        UploadService.UploadUrlResponse url = uploads.getUploadUrl(userId, created.id());
        var attempt = attempts.findById(url.uploadAttemptId()).orElseThrow();
        storage.putBytes(attempt.getStagingKey(), original, "video/mp4");
        uploads.complete(userId, created.id(), url.uploadAttemptId());
        MediaAsset source = sourceAsset(url.uploadAttemptId());

        ImportJob job = jobs.findById(created.id()).orElseThrow();
        job.claimForProcessing("old-worker", Instant.now().plusSeconds(60), Clock.systemUTC());
        jobs.saveAndFlush(job);
        ImportLease stale = ImportLease.from(job);
        job.claimForProcessing("new-worker", Instant.now().plusSeconds(60), Clock.systemUTC());
        jobs.saveAndFlush(job);

        assertThatThrownBy(() -> copies.copy(stale, new ImportLeaseControl()))
                .isInstanceOf(com.reelcipe.imports.domain.LeaseLostException.class);
        assertThat(sourceAsset(url.uploadAttemptId()).getProcessingKey()).isNull();
        assertThat(storage.bytes(
                "processing/" + stale.userId()
                        + "/" + stale.importId()
                        + "/" + source.getId()
                        + "/lease-1/source"))
                .containsExactly(original);

        ImportLease current = ImportLease.from(jobs.findById(created.id()).orElseThrow());
        copies.copy(current, new ImportLeaseControl());
        assertThat(sourceAsset(url.uploadAttemptId()).getProcessingKey()).contains("lease-2");
    }

    private ImportService.ImportView createUpload(UUID userId, long sizeBytes) {
        return imports.create(
                userId,
                "copy-" + UUID.randomUUID(),
                new ImportService.ImportCommand(
                        UUID.randomUUID(),
                        ImportSourceType.UPLOAD,
                        null,
                        ImportMediaKind.VIDEO,
                        "source.mp4",
                        "video/mp4",
                        sizeBytes,
                        null));
    }

    private MediaAsset sourceAsset(UUID attemptId) {
        return assets.findByUploadAttemptId(attemptId).orElseThrow();
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(new User(id, "Processing copy test", UserStatus.ACTIVE, now, now));
        return id;
    }
}
