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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.role=api",
        "spring.profiles.active=test",
        "app.storage.provider=memory",
        "app.import.upload-url-ttl=PT0S"
})
@Testcontainers
class UploadIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired
    private ImportService imports;

    @Autowired
    private UploadService uploads;

    @Autowired
    private UploadAttemptRepository attempts;

    @Autowired
    private MediaAssetRepository assets;

    @Autowired
    private ImportJobRepository jobs;

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
    void completeChecksSizeAndQueuesOnlyCurrentAttempt() {
        UUID userId = createUser();
        ImportService.ImportView created = createUpload(userId, 12L);
        UploadService.UploadUrlResponse url = uploads.getUploadUrl(userId, created.id());
        var attempt = attempts.findById(url.uploadAttemptId()).orElseThrow();
        storage.put(attempt.getStagingKey(), 11, "video/mp4");

        assertThatThrownBy(() -> uploads.complete(userId, created.id(), url.uploadAttemptId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(409);
        assertThat(jobs.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(ImportStatus.AWAITING_UPLOAD);
    }

    @Test
    void successfulCompleteIsIdempotentAndRejectsOldAttempt() {
        UUID userId = createUser();
        ImportService.ImportView created = createUpload(userId, 12L);
        UploadService.UploadUrlResponse firstUrl = uploads.getUploadUrl(userId, created.id());
        var first = attempts.findById(firstUrl.uploadAttemptId()).orElseThrow();
        storage.put(first.getStagingKey(), 12, "video/mp4");
        UploadService.UploadCompletionResponse completed = uploads.complete(
                userId, created.id(), firstUrl.uploadAttemptId());

        assertThat(completed.status()).isEqualTo(ImportStatus.QUEUED);
        assertThat(uploads.complete(userId, created.id(), firstUrl.uploadAttemptId()))
                .isEqualTo(completed);
        assertThat(assets.findByUploadAttemptId(firstUrl.uploadAttemptId())).isPresent();
    }

    @Test
    void expiredUrlCreatesNewAttemptAndSupersedesOldOne() {
        UUID userId = createUser();
        ImportService.ImportView created = createUpload(userId, 12L);
        UploadService.UploadUrlResponse first = uploads.getUploadUrl(userId, created.id());
        UploadService.UploadUrlResponse second = uploads.getUploadUrl(userId, created.id());

        assertThat(second.uploadAttemptId()).isNotEqualTo(first.uploadAttemptId());
        assertThat(attempts.findById(first.uploadAttemptId()).orElseThrow().getStatus())
                .isEqualTo(UploadAttemptStatus.SUPERSEDED);
        assertThatThrownBy(() -> uploads.complete(userId, created.id(), first.uploadAttemptId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(409);
    }

    @Test
    void reconcilerCompletesPutWithoutCallback() {
        UUID userId = createUser();
        ImportService.ImportView created = createUpload(userId, 12L);
        UploadService.UploadUrlResponse url = uploads.getUploadUrl(userId, created.id());
        var attempt = attempts.findById(url.uploadAttemptId()).orElseThrow();
        storage.put(attempt.getStagingKey(), 12, "video/mp4");
        UploadReconciler reconciler = new UploadReconciler(
                attempts, uploads, Clock.systemUTC(), 20);

        assertThat(reconciler.reconcileOnce()).isEqualTo(1);
        assertThat(jobs.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(ImportStatus.QUEUED);
        assertThat(assets.findByUploadAttemptId(url.uploadAttemptId())).isPresent();
    }

    private ImportService.ImportView createUpload(UUID userId, long sizeBytes) {
        return imports.create(
                userId,
                "upload-" + UUID.randomUUID(),
                new ImportService.ImportCommand(
                        UUID.randomUUID(),
                        ImportSourceType.UPLOAD,
                        null,
                        ImportMediaKind.VIDEO,
                        "recipe.mp4",
                        "video/mp4",
                        sizeBytes,
                        null));
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(new User(id, "Upload test", UserStatus.ACTIVE, now, now));
        return id;
    }
}
