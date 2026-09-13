package com.reelcipe.recipes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.idempotency.IdempotencyService;
import com.reelcipe.recipes.domain.*;
import com.reelcipe.sync.SyncChangeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock RecipeRepository recipes;
    @Mock RecipeIngredientRepository ingredients;
    @Mock RecipeStepRepository steps;
    @Mock RecipeRevisionRepository revisions;
    @Mock IdempotencyService idempotency;
    @Mock SyncChangeService sync;

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
}
