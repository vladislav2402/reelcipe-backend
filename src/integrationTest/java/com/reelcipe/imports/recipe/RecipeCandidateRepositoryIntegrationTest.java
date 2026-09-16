package com.reelcipe.imports.recipe;

import com.reelcipe.auth.domain.User;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.recipe.domain.RecipeCandidate;
import com.reelcipe.imports.recipe.domain.RecipeCandidateRepository;
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

@SpringBootTest(properties = {"app.role=api", "spring.profiles.active=test"})
@Testcontainers
class RecipeCandidateRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired
    private RecipeCandidateRepository candidates;
    @Autowired
    private ImportJobRepository jobs;
    @Autowired
    private UserRepository users;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void persistsImmutableCandidateAndFindsItByInputHash() {
        UUID userId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(new User(userId, "Candidate test", UserStatus.ACTIVE, now, now));
        jobs.saveAndFlush(new ImportJob(
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
                "import-input-hash",
                ImportStatus.EXTRACTING_RECIPE,
                ImportStage.EXTRACTING_RECIPE,
                now.plusSeconds(3600),
                null,
                now));
        RecipeCandidate candidate = candidates.saveAndFlush(new RecipeCandidate(
                UUID.randomUUID(),
                importId,
                userId,
                "recipe-input-hash",
                null,
                null,
                "recipe-extraction-v1",
                "recipe-extraction-v1",
                "b21-v1",
                "en",
                "DESCRIPTION_ONLY",
                "{\"title\":\"Pasta\"}",
                "mock",
                "mock-v1",
                now));

        RecipeCandidate stored = candidates
                .findTopByImportIdAndInputHashOrderByCreatedAtDesc(importId, "recipe-input-hash")
                .orElseThrow();

        assertThat(stored.getId()).isEqualTo(candidate.getId());
        assertThat(stored.getCandidateJson()).contains("Pasta");
        assertThat(stored.getSourceCoverage()).isEqualTo("DESCRIPTION_ONLY");
    }
}
