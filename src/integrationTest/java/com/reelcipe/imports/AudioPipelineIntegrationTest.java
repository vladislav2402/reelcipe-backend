package com.reelcipe.imports;

import com.reelcipe.auth.domain.User;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.audio.AudioExtractor;
import com.reelcipe.imports.audio.AudioPipelineService;
import com.reelcipe.imports.domain.*;
import com.reelcipe.operations.deletion.domain.DeletionTaskRepository;
import com.reelcipe.storage.InMemoryObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.role=worker",
        "app.worker.enabled=false",
        "spring.profiles.active=test",
        "app.storage.provider=memory",
        "app.media-tools.provider=test"
})
@Import(AudioPipelineIntegrationTest.FakeAudioConfiguration.class)
@Testcontainers
class AudioPipelineIntegrationTest {
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
    private AudioPipelineService audio;

    @Autowired
    private ImportJobRepository jobs;

    @Autowired
    private MediaAssetRepository assets;

    @Autowired
    private DeletionTaskRepository deletions;

    @Autowired
    private UserRepository users;

    @Autowired
    private UploadAttemptRepository attempts;

    @Autowired
    private InMemoryObjectStorage storage;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void persistsNormalizedAudioAndSchedulesSourceDeletion() {
        UUID userId = createUser();
        byte[] sourceBytes = "fake-media-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ImportService.ImportView created = imports.create(
                userId,
                "audio-" + UuidV7.randomUuid(),
                new ImportService.ImportCommand(
                        UuidV7.randomUuid(),
                        ImportSourceType.UPLOAD,
                        null,
                        ImportMediaKind.VIDEO,
                        "fixture.mp4",
                        "video/mp4",
                        (long) sourceBytes.length,
                        null));
        UploadService.UploadUrlResponse url = uploads.getUploadUrl(userId, created.id());
        String stagingKey = attempts.findById(url.uploadAttemptId()).orElseThrow().getStagingKey();
        storage.putBytes(stagingKey, sourceBytes, "video/mp4");
        uploads.complete(userId, created.id(), url.uploadAttemptId());

        ImportLease resolving = queue.claimNext("copy-worker").orElseThrow();
        copies.copy(resolving, new ImportLeaseControl());
        ImportLease extracting = queue.claimNext("audio-worker").orElseThrow();
        audio.extract(extracting, new ImportLeaseControl());

        MediaAsset normalized = assets.findByImportIdAndAssetType(
                        created.id(), MediaAssetType.NORMALIZED_AUDIO)
                .orElseThrow();
        assertThat(normalized.getProcessingKey()).startsWith("processing/");
        assertThat(normalized.getContentType()).isEqualTo("audio/flac");
        assertThat(normalized.getDurationSeconds()).isEqualTo(2);
        assertThat(storage.bytes(normalized.getProcessingKey())).containsExactly(sourceBytes);
        assertThat(jobs.findById(created.id()).orElseThrow())
                .satisfies(job -> {
                    assertThat(job.getStatus()).isEqualTo(ImportStatus.TRANSCRIBING);
                    assertThat(job.getAudioOutcome()).isEqualTo(AudioOutcome.AUDIO_READY);
                });
        assertThat(deletions.findByDeduplicationKey("source-processing:" + created.id()))
                .isPresent();
    }

    private UUID createUser() {
        UUID id = UuidV7.randomUuid();
        Instant now = Instant.now();
        users.saveAndFlush(new User(id, "Audio pipeline test", UserStatus.ACTIVE, now, now));
        return id;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAudioConfiguration {
        @Bean
        AudioExtractor audioExtractor() {
            return new FakeAudioExtractor();
        }
    }

    private static final class FakeAudioExtractor implements AudioExtractor {
        @Override
        public Probe probe(Path input) {
            if (input.getFileName().toString().endsWith(".flac")) {
                return new Probe(Duration.ofSeconds(2), 1, 16000, 1, "flac");
            }
            return new Probe(Duration.ofSeconds(2), 1, 48000, 2, "h264");
        }

        @Override
        public void extract(Path input, Path output) {
            try {
                Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
