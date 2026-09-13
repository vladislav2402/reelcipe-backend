package com.reelcipe.recipes;

import com.reelcipe.auth.FixtureUserInitializer;
import com.reelcipe.recipes.domain.*;
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
class RecipeRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    RecipeRepository recipes;
    @Autowired
    RecipeIngredientRepository ingredients;
    @Autowired
    RecipeStepRepository steps;
    @Autowired
    RecipeRevisionRepository revisions;

    @Test
    void repositoriesPersistManualRecipeAggregate() {
        UUID userId = FixtureUserInitializer.ALICE_ID;
        UUID recipeId = UUID.randomUUID();
        Recipe recipe = new Recipe(recipeId, userId, "Pasta", RecipeLanguage.EN, UUID.randomUUID(),
                RecipeAnalysisMode.MANUAL, RecipeLibraryState.SAVED, Instant.now());
        recipes.save(recipe);
        ingredients.save(new RecipeIngredient(UUID.randomUUID(), recipeId, 0, "Flour", null, null, "g", "flour"));
        steps.save(new RecipeStep(UUID.randomUUID(), recipeId, 0, "Mix ingredients"));
        revisions.save(new RecipeRevision(recipeId, 1, userId, "{}", Instant.now()));

        assertThat(recipes.findByIdAndUserIdAndDeletedAtIsNull(recipeId, userId)).isPresent();
        assertThat(ingredients.findByRecipeIdOrderByPosition(recipeId)).hasSize(1);
        assertThat(steps.findByRecipeIdOrderByPosition(recipeId)).hasSize(1);
        assertThat(revisions.count()).isPositive();
    }
}
