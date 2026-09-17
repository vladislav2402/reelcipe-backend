package com.reelcipe.imports;

import com.reelcipe.auth.domain.User;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.billing.domain.QuotaReservationRepository;
import com.reelcipe.billing.domain.QuotaReservationState;
import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.audio.AudioExtractor;
import com.reelcipe.imports.audio.AudioPipelineService;
import com.reelcipe.imports.domain.ImportLease;
import com.reelcipe.imports.domain.ImportMediaKind;
import com.reelcipe.imports.domain.ImportSourceType;
import com.reelcipe.imports.domain.ImportStatus;
import com.reelcipe.imports.recipe.RecipeExtractionPipelineService;
import com.reelcipe.imports.recipe.RecipeFinalizationService;
import com.reelcipe.imports.transcription.MockSpeechTranscriber;
import com.reelcipe.imports.transcription.TranscriptionPipelineService;
import com.reelcipe.recipes.RecipeService;
import com.reelcipe.recipes.domain.RecipeImportResultRepository;
import com.reelcipe.recipes.domain.RecipeLibraryState;
import com.reelcipe.shopping.ShoppingService;
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
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.role=worker",
        "app.worker.enabled=false",
        "spring.profiles.active=test",
        "app.storage.provider=memory",
        "app.media-tools.provider=test",
        "app.asr.provider=mock",
        "app.llm.provider=mock"
})
@Import(MockPipelineIntegrationTest.TestAudioConfiguration.class)
@Testcontainers
class MockPipelineIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres:17.6-alpine");

    @Autowired
    private UserRepository users;
    @Autowired
    private ImportService imports;
    @Autowired
    private UploadService uploads;
    @Autowired
    private ImportQueue queue;
    @Autowired
    private com.reelcipe.imports.domain.ImportJobRepository jobs;
    @Autowired
    private ProcessingCopyService copies;
    @Autowired
    private AudioPipelineService audio;
    @Autowired
    private TranscriptionPipelineService transcription;
    @Autowired
    private RecipeExtractionPipelineService extraction;
    @Autowired
    private RecipeFinalizationService finalization;
    @Autowired
    private RecipeService recipes;
    @Autowired
    private ShoppingService shopping;
    @Autowired
    private RecipeImportResultRepository recipeImports;
    @Autowired
    private QuotaReservationRepository reservations;
    @Autowired
    private com.reelcipe.imports.domain.UploadAttemptRepository uploadAttempts;
    @Autowired
    private InMemoryObjectStorage storage;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void runsUploadThroughMockProvidersSaveAndShopping() {
        UUID userId = createUser();
        byte[] source = MockSpeechTranscriber.recipeFixture();
        ImportService.ImportView created = imports.create(
                userId,
                "mock-pipeline-" + UuidV7.randomUuid(),
                new ImportService.ImportCommand(
                        UuidV7.randomUuid(),
                        ImportSourceType.UPLOAD,
                        null,
                        ImportMediaKind.VIDEO,
                        "fixture.mp4",
                        "video/mp4",
                        (long) source.length,
                        "Add pasta to boiling water and season with salt."));

        UploadService.UploadUrlResponse upload = uploads.getUploadUrl(userId, created.id());
        String stagingKey = uploadAttempts.findById(upload.uploadAttemptId())
                .orElseThrow().getStagingKey();
        storage.putBytes(stagingKey, source, "video/mp4");
        uploads.complete(userId, created.id(), upload.uploadAttemptId());
        assertThat(jobs.findById(created.id()).orElseThrow())
                .satisfies(job -> {
                    assertThat(job.getStatus()).isEqualTo(ImportStatus.QUEUED);
                    assertThat(job.getNextAttemptAt()).isNotNull();
                    assertThat(job.getProcessingDeadlineAt()).isAfter(Instant.now());
                });

        copies.copy(claim("copy-worker"), new ImportLeaseControl());
        audio.extract(claim("audio-worker"), new ImportLeaseControl());
        transcription.transcribe(claim("asr-worker"), new ImportLeaseControl());
        extraction.extract(claim("llm-worker"), new ImportLeaseControl());
        finalization.finalizeImport(claim("finalizer-worker"));

        UUID recipeId = recipeImports.findByImportId(created.id()).orElseThrow().getRecipeId();
        RecipeService.RecipeView saved = recipes.save(
                userId,
                recipeId,
                "save-" + UuidV7.randomUuid());
        assertThat(saved.libraryState()).isEqualTo(RecipeLibraryState.SAVED);

        ShoppingService.ShoppingView list = shopping.addRecipe(
                userId,
                "shopping-" + UuidV7.randomUuid(),
                new ShoppingService.RecipeAddition(
                        recipeId,
                        saved.version(),
                        UuidV7.randomUuid()));

        assertThat(list.items()).extracting(ShoppingService.ItemView::name)
                .contains("pasta");
        assertThat(reservations.findTopByImportIdOrderByGenerationDesc(created.id())
                .orElseThrow().getState()).isEqualTo(QuotaReservationState.CONSUMED);
        assertThat(imports.get(userId, created.id()).status())
                .isIn(ImportStatus.READY, ImportStatus.REVIEW_REQUIRED);
    }

    private ImportLease claim(String workerId) {
        return queue.claimNext(workerId).orElseThrow();
    }

    private UUID createUser() {
        UUID id = UuidV7.randomUuid();
        Instant now = Instant.now();
        users.saveAndFlush(new User(id, "Mock pipeline test", UserStatus.ACTIVE, now, now));
        return id;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestAudioConfiguration {
        @Bean
        AudioExtractor audioExtractor() {
            return new AudioExtractor() {
                @Override
                public Probe probe(Path input) {
                    return new Probe(java.time.Duration.ofSeconds(4), 1, 16000, 1, "flac");
                }

                @Override
                public void extract(Path input, Path output) {
                    try {
                        Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            };
        }
    }
}
