package com.reelcipe.recipes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.idempotency.IdempotencyService;
import com.reelcipe.recipes.domain.*;
import com.reelcipe.sync.SyncChangeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    RecipeRepository recipes;
    @Mock
    RecipeIngredientRepository ingredients;
    @Mock
    RecipeStepRepository steps;
    @Mock
    RecipeRevisionRepository revisions;
    @Mock
    IdempotencyService idempotency;
    @Mock
    SyncChangeService sync;

    @Test
    void patchRejectsStaleIfMatchVersion() {
        RecipeService service = new RecipeService(recipes, ingredients, steps, revisions, idempotency, sync, new ObjectMapper());
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        Recipe recipe = new Recipe(recipeId, userId, "Old", RecipeLanguage.EN, null,
                RecipeAnalysisMode.MANUAL, RecipeLibraryState.SAVED, Instant.now());
        when(recipes.findForUpdate(recipeId, userId)).thenReturn(Optional.of(recipe));
        when(idempotency.execute(any(), any(), any(), any(), any(), any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<com.reelcipe.idempotency.domain.IdempotencyResult>) invocation.getArgument(5)).get());

        assertThrows(ResponseStatusException.class, () -> service.patch(userId, recipeId, 2,
                "key", new RecipeService.RecipePatch("New", RecipeLanguage.EN, null, null)));
    }

    @Test
    void listRejectsCursorWithDifferentSearch() {
        RecipeService service = new RecipeService(recipes, ingredients, steps, revisions, idempotency, sync,
                new ObjectMapper());
        UUID userId = UUID.randomUUID();
        Recipe recipe = new Recipe(UUID.randomUUID(), userId, "Pasta", RecipeLanguage.EN, null,
                RecipeAnalysisMode.MANUAL, RecipeLibraryState.SAVED, Instant.now());
        when(recipes.findLibrary(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(recipe, recipe));
        when(ingredients.findByRecipeIdOrderByPosition(any())).thenReturn(List.of());
        when(steps.findByRecipeIdOrderByPosition(any())).thenReturn(List.of());

        RecipeService.RecipePage firstPage = service.list(userId, "pasta", 1, null);

        assertThrows(ResponseStatusException.class,
                () -> service.list(userId, "salt", 1, firstPage.nextCursor()));
    }

    @Test
    void listRejectsLimitOutsideContract() {
        RecipeService service = new RecipeService(recipes, ingredients, steps, revisions, idempotency, sync,
                new ObjectMapper());

        assertThrows(ResponseStatusException.class, () -> service.list(UUID.randomUUID(), null, 101, null));
    }

    @Test
    void savePublishesDraftWithoutCallingQuotaOrAi() {
        RecipeService service = new RecipeService(
                recipes,
                ingredients,
                steps,
                revisions,
                idempotency,
                sync,
                new ObjectMapper().findAndRegisterModules());
        UUID userId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        Recipe recipe = new Recipe(
                recipeId,
                userId,
                "Imported pasta",
                RecipeLanguage.EN,
                null,
                RecipeAnalysisMode.IMPORT,
                RecipeLibraryState.DRAFT,
                Instant.now());
        when(recipes.findForUpdate(recipeId, userId)).thenReturn(Optional.of(recipe));
        when(ingredients.findByRecipeIdOrderByPosition(recipeId)).thenReturn(List.of());
        when(steps.findByRecipeIdOrderByPosition(recipeId)).thenReturn(List.of());
        when(idempotency.execute(any(), any(), any(), any(), any(), any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<com.reelcipe.idempotency.domain.IdempotencyResult>) invocation.getArgument(5)).get());

        RecipeService.RecipeView saved = service.save(userId, recipeId, "save-key");

        assertThat(saved.libraryState()).isEqualTo(RecipeLibraryState.SAVED);
        assertThat(saved.version()).isEqualTo(2);
        verify(sync).record(userId, "recipe", recipeId, "UPDATE", 2);
    }
}
