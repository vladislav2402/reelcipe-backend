package com.reelcipe.recipes;

import com.reelcipe.auth.domain.AuthenticatedUser;
import com.reelcipe.common.UuidV7;
import com.reelcipe.recipes.domain.RecipeLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeControllerTest {

    @Mock
    RecipeService service;

    @Test
    void createReturnsRecipeVersionInResponse() {
        RecipeController controller = new RecipeController(service);
        UUID userId = UuidV7.randomUuid();
        UUID recipeId = UuidV7.randomUuid();
        AuthenticatedUser user = new AuthenticatedUser(userId, UuidV7.randomUuid());
        RecipeService.RecipeView view = new RecipeService.RecipeView(recipeId, "Pasta", RecipeLanguage.EN, null,
                com.reelcipe.recipes.domain.RecipeLibraryState.SAVED, 1, Instant.now(), Instant.now(), List.of(), List.of());
        when(service.create(any(), any(), any())).thenReturn(view);

        var response = controller.create(user, "key", new RecipeController.RecipeRequest("Pasta", RecipeLanguage.EN, List.of(), List.of()));

        assertEquals("\"1\"", response.getHeaders().getETag());
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void patchRejectsMalformedIfMatch() {
        RecipeController controller = new RecipeController(service);
        assertThrows(ResponseStatusException.class, () -> controller.patch(
                new AuthenticatedUser(UuidV7.randomUuid(), UuidV7.randomUuid()), UuidV7.randomUuid(), "bad", "key",
                new RecipeController.RecipeRequest("Pasta", RecipeLanguage.EN, List.of(), List.of())));
    }

    @Test
    void saveReturnsUpdatedDraftVersion() {
        RecipeController controller = new RecipeController(service);
        UUID recipeId = UuidV7.randomUuid();
        RecipeService.RecipeView view = new RecipeService.RecipeView(
                recipeId,
                "Pasta",
                RecipeLanguage.EN,
                null,
                com.reelcipe.recipes.domain.RecipeLibraryState.SAVED,
                2,
                Instant.now(),
                Instant.now(),
                List.of(),
                List.of());
        when(service.save(any(), any(), any())).thenReturn(view);

        var response = controller.save(
                new AuthenticatedUser(UuidV7.randomUuid(), UuidV7.randomUuid()), recipeId, "save-key");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("\"2\"", response.getHeaders().getETag());
    }
}
