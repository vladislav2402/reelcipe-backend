package com.reelcipe.imports;

import com.reelcipe.auth.domain.User;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.imports.audio.AudioExtractor;
import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.transcription.MockSpeechTranscriber;
import com.reelcipe.imports.transcription.TranscriptionPipelineService;
import com.reelcipe.imports.transcription.domain.*;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.role=worker",
        "app.worker.enabled=false",
        "spring.profiles.active=test",
        "app.storage.provider=memory",
        "app.media-tools.provider=test",
        "app.asr.provider=mock"
})
@Import(TranscriptionPipelineIntegrationTest.TestAudioConfiguration.class)
@Testcontainers
class TranscriptionPipelineIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired
    private ImportJobRepository jobs;

    @Autowired
    private ImportQueue queue;

    @Autowired
    private MediaAssetRepository assets;

    @Autowired
    private TranscriptionPipelineService transcription;

    @Autowired
    private TranscriptionRepository transcriptions;

    @Autowired
    private TranscriptionSegmentRepository segments;

    @Autowired
    private AiAttemptRepository attempts;

    @Autowired
    private DeletionTaskRepository deletions;

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
    void transcribesKnownFixtureAndPersistsCheckpoint() {
        UUID userId = createUser();
        UUID importId = UUID.randomUUID();
        Instant now = Instant.now();
        ImportJob job = new ImportJob(
                importId,
                userId,
                UUID.randomUUID(),
                ImportSourceType.UPLOAD,
                null,
                ImportMediaKind.AUDIO,
                "fixture.flac",
                "audio/flac",
                100L,
                null,
                "fixture-hash",
                ImportStatus.TRANSCRIBING,
                ImportStage.TRANSCRIBING,
                now.plusSeconds(3600),
                null,
                now);
        jobs.saveAndFlush(job);

        UUID audioAssetId = UUID.randomUUID();
        String audioKey = "processing/" + importId + "/audio.flac";
        assets.saveAndFlush(MediaAsset.normalizedAudio(
                audioAssetId,
                importId,
                userId,
                audioKey,
                "fixture-audio-hash",
                MockSpeechTranscriber.recipeFixture().length,
                4,
                now.plusSeconds(3600),
                now));
        storage.putBytes(audioKey, MockSpeechTranscriber.recipeFixture(), "audio/flac");

        ImportLease lease = queue.claimNext("asr-worker").orElseThrow();
        transcription.transcribe(lease, new ImportLeaseControl());

        assertThat(jobs.findById(importId).orElseThrow().getStatus())
                .isEqualTo(ImportStatus.EXTRACTING_RECIPE);
        var saved = transcriptions.findTopByImportIdOrderByVersionDesc(importId).orElseThrow();
        assertThat(saved.getSpeechStatus().name()).isEqualTo("SPEECH");
        assertThat(saved.getFullText()).contains("Add pasta");
        assertThat(segments.findByTranscriptionIdOrderBySegmentIndex(saved.getId()))
                .hasSize(2)
                .allSatisfy(segment -> {
                    assertThat(segment.getStartMs()).isGreaterThanOrEqualTo(0);
                    assertThat(segment.getEndMs()).isGreaterThan(segment.getStartMs());
                });
        AiAttempt attempt = attempts
                .findTopByImportIdAndKindOrderByAttemptNumberDesc(importId, AiAttemptKind.ASR)
                .orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo(AiAttemptStatus.SUCCEEDED);
        assertThat(deletions.findByDeduplicationKey(
                "normalized-audio:" + importId + ":" + audioAssetId)).isPresent();
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(new User(id, "ASR integration test", UserStatus.ACTIVE, now, now));
        return id;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestAudioConfiguration {
        @Bean
        AudioExtractor audioExtractor() {
            return new AudioExtractor() {
                @Override
                public Probe probe(java.nio.file.Path input) {
                    return new Probe(java.time.Duration.ofSeconds(4), 1, 16000, 1, "flac");
                }

                @Override
                public void extract(java.nio.file.Path input, java.nio.file.Path output) {
                }
            };
        }
    }
}
