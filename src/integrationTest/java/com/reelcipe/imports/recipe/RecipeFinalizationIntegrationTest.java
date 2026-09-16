package com.reelcipe.imports.recipe;

import com.reelcipe.auth.domain.User;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.billing.QuotaService;
import com.reelcipe.billing.domain.QuotaReservationRepository;
import com.reelcipe.billing.domain.QuotaReservationState;
import com.reelcipe.imports.ImportQueue;
import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.recipe.domain.RecipeCandidate;
import com.reelcipe.imports.recipe.domain.RecipeCandidateRepository;
import com.reelcipe.recipes.domain.RecipeEvidenceRepository;
import com.reelcipe.recipes.domain.RecipeImportResultRepository;
import com.reelcipe.recipes.domain.RecipeLibraryState;
import com.reelcipe.recipes.domain.RecipeRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.role=worker", "app.worker.enabled=false"})
@Testcontainers
class RecipeFinalizationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired
    private UserRepository users;
    @Autowired
    private ImportJobRepository jobs;
    @Autowired
    private ImportQueue queue;
    @Autowired
    private RecipeCandidateRepository candidates;
    @Autowired
    private RecipeFinalizationService finalization;
    @Autowired
    private RecipeRepository recipes;
    @Autowired
    private RecipeImportResultRepository imports;
    @Autowired
    private RecipeEvidenceRepository evidence;
    @Autowired
    private QuotaService quota;
    @Autowired
    private QuotaReservationRepository reservations;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void createsOneDraftEvidenceAndConsumesReservationAtomically() {
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(new User(userId, "Finalization test", UserStatus.ACTIVE, now, now));
        ImportJob job = new ImportJob(
                importId,
                userId,
                UUID.randomUUID(),
                ImportSourceType.LINK,
                "https://example.test/recipe",
                null,
                null,
                null,
                null,
                "Boil pasta.",
                "import-hash",
                ImportStatus.VALIDATING,
                ImportStage.VALIDATING,
                now.plusSeconds(3600),
                null,
                now);
        jobs.saveAndFlush(job);
        quota.reserve(userId, importId);
        RecipeTextSnapshot snapshot = new RecipeTextSnapshot(
                null,
                0,
                null,
                List.of(),
                "Boil pasta.",
                null,
                null,
                null,
                "en",
                "en",
                "recipe-extraction-v1",
                "recipe-extraction-v1",
                "b21-v1",
                "mock",
                "mock-v1");
        UUID candidateId = UUID.randomUUID();
        candidates.saveAndFlush(new RecipeCandidate(
                candidateId,
                importId,
                userId,
                snapshot.inputHash(),
                null,
                null,
                "recipe-extraction-v1",
                "recipe-extraction-v1",
                "b21-v1",
                "EN",
                "DESCRIPTION_ONLY",
                candidateJson(),
                "mock",
                "mock-v1",
                now));
        job = jobs.findById(importId).orElseThrow();
        job.checkpoint(ImportStage.VALIDATING, candidateId.toString(), java.time.Clock.systemUTC());
        jobs.saveAndFlush(job);

        ImportLease lease = queue.claimNext("finalizer-test").orElseThrow();
        finalization.finalizeImport(lease);

        assertThat(jobs.findById(importId).orElseThrow().getStatus())
                .isEqualTo(ImportStatus.REVIEW_REQUIRED);
        var recipeImport = imports.findByImportId(importId).orElseThrow();
        assertThat(recipes.findById(recipeImport.getRecipeId()).orElseThrow().getLibraryState())
                .isEqualTo(RecipeLibraryState.DRAFT);
        assertThat(evidence.findAll()).hasSize(1);
        assertThat(reservations.findTopByImportIdOrderByGenerationDesc(importId).orElseThrow()
                .getState()).isEqualTo(QuotaReservationState.CONSUMED);
    }

    private String candidateJson() {
        return "{\"title\":\"Pasta\",\"language\":\"EN\","
                + "\"description\":\"Boil pasta.\",\"ingredients\":[],"
                + "\"steps\":[{\"position\":1,\"text\":\"Boil pasta.\","
                + "\"evidence\":[{\"source\":\"AUTHOR_DESCRIPTION\","
                + "\"segmentIndex\":null,\"quote\":\"Boil pasta.\"}]}]}";
    }
}
