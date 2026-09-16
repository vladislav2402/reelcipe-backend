package com.reelcipe.recipes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.idempotency.IdempotencyService;
import com.reelcipe.idempotency.domain.IdempotencyResult;
import com.reelcipe.recipes.domain.*;
import com.reelcipe.sync.SyncChangeService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecipeService {
    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final RecipeStepRepository steps;
    private final RecipeRevisionRepository revisions;
    private final IdempotencyService idempotency;
    private final SyncChangeService sync;
    private final ObjectMapper objectMapper;

    public RecipeService(RecipeRepository recipes, RecipeIngredientRepository ingredients, RecipeStepRepository steps,
                         RecipeRevisionRepository revisions, IdempotencyService idempotency, SyncChangeService sync, ObjectMapper objectMapper) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.steps = steps;
        this.revisions = revisions;
        this.idempotency = idempotency;
        this.sync = sync;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecipeView create(UUID userId, String key, RecipeCommand command) {
        String body = json(command);
        IdempotencyResult result = idempotency.execute(userId, "recipe.create", "recipes", key, body, () ->
                IdempotencyResult.created(json(createNew(userId, command))));
        return readJson(result.body(), RecipeView.class);
    }

    @Transactional(readOnly = true)
    public RecipeView get(UUID userId, UUID recipeId) {
        Recipe recipe = recipes.findByIdAndUserIdAndDeletedAtIsNull(recipeId, userId).orElseThrow(this::notFound);
        return view(recipe);
    }

    @Transactional(readOnly = true)
    public RecipePage list(UUID userId, String search, int requestedLimit, String cursor) {
        int limit = validateLimit(requestedLimit);
        String normalizedSearch = normalizeSearch(search);
        CursorPayload payload = decodeCursor(cursor);
        validateCursor(payload, normalizedSearch);
        PageRequest pageRequest = PageRequest.of(0, limit + 1);
        List<Recipe> recipesPage = payload == null
                ? recipes.findLibrary(userId, RecipeLibraryState.SAVED, normalizedSearch, pageRequest)
                : recipes.findLibraryAfter(
                userId,
                RecipeLibraryState.SAVED,
                normalizedSearch,
                Instant.parse(payload.updatedAt()),
                payload.id(),
                pageRequest);
        boolean hasNext = recipesPage.size() > limit;
        List<Recipe> page = hasNext ? recipesPage.subList(0, limit) : recipesPage;
        String nextCursor = hasNext ? encodeCursor(normalizedSearch, page.get(page.size() - 1)) : null;
        return new RecipePage(page.stream().map(this::view).toList(), nextCursor);
    }

    @Transactional
    public RecipeView patch(UUID userId, UUID recipeId, long expectedVersion, String key, RecipePatch command) {
        return executeMutation(userId, recipeId, key, "recipe.update", command, () -> {
            Recipe recipe = locked(userId, recipeId, expectedVersion);
            recipe.update(command.title(), command.language(), Instant.now());
            replaceChildren(recipe, command.ingredients(), command.steps(), true);
            revisions.save(new RecipeRevision(recipe.getId(), recipe.getVersion(), userId, json(view(recipe)), Instant.now()));
            sync.record(userId, "recipe", recipe.getId(), "UPDATE", recipe.getVersion());
            return view(recipe);
        });
    }

    @Transactional
    public void delete(UUID userId, UUID recipeId, long expectedVersion, String key) {
        executeMutation(userId, recipeId, key, "recipe.delete", "delete", () -> {
            Recipe recipe = locked(userId, recipeId, expectedVersion);
            recipe.delete(Instant.now());
            sync.record(userId, "recipe", recipe.getId(), "DELETE", recipe.getVersion());
            return view(recipe);
        });
    }

    @Transactional
    public RecipeView save(UUID userId, UUID recipeId, String key) {
        return executeMutation(userId, recipeId, key, "recipe.save", "save", () -> {
            Recipe recipe = recipes.findForUpdate(recipeId, userId).orElseThrow(this::notFound);
            if (recipe.getLibraryState() == RecipeLibraryState.SAVED) {
                return view(recipe);
            }
            recipe.save(Instant.now());
            revisions.save(new RecipeRevision(
                    recipe.getId(), recipe.getVersion(), userId, json(view(recipe)), Instant.now()));
            sync.record(userId, "recipe", recipe.getId(), "UPDATE", recipe.getVersion());
            return view(recipe);
        });
    }

    private RecipeView createNew(UUID userId, RecipeCommand command) {
        Recipe recipe = new Recipe(
                UUID.randomUUID(),
                userId,
                command.title(),
                command.language(),
                null,
                RecipeAnalysisMode.MANUAL,
                RecipeLibraryState.SAVED,
                Instant.now());
        recipes.save(recipe);
        replaceChildren(recipe, command.ingredients(), command.steps(), false);
        revisions.save(new RecipeRevision(recipe.getId(), recipe.getVersion(), userId, json(view(recipe)), Instant.now()));
        sync.record(userId, "recipe", recipe.getId(), "CREATE", recipe.getVersion());
        return view(recipe);
    }

    private RecipeView executeMutation(
            UUID userId,
            UUID recipeId,
            String key,
            String operation,
            Object body,
            java.util.function.Supplier<RecipeView> action) {
        IdempotencyResult result = idempotency.execute(userId, operation, recipeId.toString(), key, json(body), () -> IdempotencyResult.ok(json(action.get())));
        return readJson(result.body(), RecipeView.class);
    }

    private Recipe locked(UUID userId, UUID id, long expected) {
        Recipe recipe = recipes.findForUpdate(id, userId).orElseThrow(this::notFound);
        if (recipe.getVersion() != expected)
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Recipe version does not match");
        return recipe;
    }

    private void replaceChildren(
            Recipe recipe,
            List<IngredientCommand> ingredientCommands,
            List<StepCommand> stepCommands,
            boolean preserveExistingIds) {
        List<RecipeIngredient> existingIngredients = preserveExistingIds
                ? ingredients.findByRecipeIdOrderByPosition(recipe.getId())
                : List.of();
        List<RecipeStep> existingSteps = preserveExistingIds
                ? steps.findByRecipeIdOrderByPosition(recipe.getId())
                : List.of();
        ingredients.deleteByRecipeId(recipe.getId());
        steps.deleteByRecipeId(recipe.getId());
        if (ingredientCommands != null) for (int i = 0; i < ingredientCommands.size(); i++) {
            IngredientCommand c = ingredientCommands.get(i);
            UUID ingredientId = i < existingIngredients.size()
                    ? existingIngredients.get(i).getId()
                    : UUID.randomUUID();
            ingredients.save(new RecipeIngredient(
                    ingredientId,
                    recipe.getId(),
                    i,
                    c.name(),
                    c.amount(),
                    c.amountMax(),
                    c.unit(),
                    c.originalText()));
        }
        if (stepCommands != null) for (int i = 0; i < stepCommands.size(); i++) {
            StepCommand c = stepCommands.get(i);
            UUID stepId = i < existingSteps.size()
                    ? existingSteps.get(i).getId()
                    : UUID.randomUUID();
            steps.save(new RecipeStep(stepId, recipe.getId(), i, c.text()));
        }
    }

    private RecipeView view(Recipe recipe) {
        List<IngredientView> ingredientViews = ingredients.findByRecipeIdOrderByPosition(recipe.getId()).stream()
                .map(ingredient -> new IngredientView(
                        ingredient.getId(),
                        ingredient.getPosition(),
                        ingredient.getName(),
                        ingredient.getAmount(),
                        ingredient.getAmountMax(),
                        ingredient.getUnit(),
                        ingredient.getOriginalText()))
                .toList();
        List<StepView> stepViews = steps.findByRecipeIdOrderByPosition(recipe.getId()).stream()
                .map(step -> new StepView(step.getId(), step.getPosition(), step.getText()))
                .toList();
        return new RecipeView(
                recipe.getId(),
                recipe.getTitle(),
                recipe.getLanguage(),
                recipe.getPreviewMediaId(),
                recipe.getLibraryState(),
                recipe.getVersion(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt(),
                ingredientViews,
                stepViews);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found");
    }

    private int validateLimit(int requestedLimit) {
        if (requestedLimit < 1 || requestedLimit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 100");
        }
        return requestedLimit;
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private CursorPayload decodeCursor(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor));
            CursorPayload payload = objectMapper.readValue(json, CursorPayload.class);
            if (payload.version() != 1 || payload.id() == null || payload.updatedAt() == null
                    || !"updated_at_desc,id_desc".equals(payload.sort())) {
                throw new IllegalArgumentException("Invalid cursor fields");
            }
            Instant.parse(payload.updatedAt());
            return payload;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid recipe cursor", exception);
        }
    }

    private void validateCursor(CursorPayload payload, String search) {
        if (payload != null && !Objects.equals(payload.search(), search)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cursor does not match search parameters");
        }
    }

    private String encodeCursor(String search, Recipe recipe) {
        CursorPayload payload = new CursorPayload(
                1,
                search,
                "updated_at_desc,id_desc",
                recipe.getUpdatedAt().toString(),
                recipe.getId());
        try {
            String json = objectMapper.writeValueAsString(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to create recipe cursor", exception);
        }
    }

    public record RecipeCommand(String title, RecipeLanguage language,
                                List<IngredientCommand> ingredients, List<StepCommand> steps) {
    }

    public record RecipePatch(String title, RecipeLanguage language,
                              List<IngredientCommand> ingredients, List<StepCommand> steps) {
    }

    public record IngredientCommand(String name, BigDecimal amount, BigDecimal amountMax, String unit,
                                    String originalText) {
    }

    public record StepCommand(String text) {
    }

    public record RecipeView(UUID id, String title, RecipeLanguage language, UUID previewMediaId,
                             RecipeLibraryState libraryState, long version, Instant createdAt, Instant updatedAt,
                             List<IngredientView> ingredients, List<StepView> steps) {
    }

    public record IngredientView(UUID id, int position, String name, BigDecimal amount, BigDecimal amountMax,
                                 String unit, String originalText) {
    }

    public record StepView(UUID id, int position, String text) {
    }

    public record RecipePage(List<RecipeView> items, String nextCursor) {
    }

    private record CursorPayload(int version, String search, String sort, String updatedAt, UUID id) {
    }
}
