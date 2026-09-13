package com.reelcipe.recipes;

import com.reelcipe.auth.domain.AuthenticatedUser;
import com.reelcipe.recipes.RecipeService.*;
import com.reelcipe.recipes.domain.RecipeLanguage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/recipes")
@Profile("!worker")
@SecurityRequirement(name = "bearerAuth")
public class RecipeController {

    private final RecipeService service;

    public RecipeController(RecipeService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a manual recipe")
    public ResponseEntity<RecipeView> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RecipeRequest request) {
        RecipeView recipe = service.create(requireUser(user).userId(), idempotencyKey, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(recipe.version())).body(recipe);
    }

    @GetMapping("/{recipeId}")
    @Operation(summary = "Get a recipe")
    public ResponseEntity<RecipeView> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID recipeId) {
        RecipeView recipe = service.get(requireUser(user).userId(), recipeId);
        return ResponseEntity.ok().eTag(etag(recipe.version())).body(recipe);
    }

    @PatchMapping("/{recipeId}")
    @Operation(summary = "Edit a recipe")
    public ResponseEntity<RecipeView> patch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID recipeId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RecipeRequest request) {
        RecipeView recipe = service.patch(requireUser(user).userId(), recipeId, parseVersion(ifMatch), idempotencyKey, request.toPatch());
        return ResponseEntity.ok().eTag(etag(recipe.version())).body(recipe);
    }

    @DeleteMapping("/{recipeId}")
    @Operation(summary = "Delete a recipe")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID recipeId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        service.delete(requireUser(user).userId(), recipeId, parseVersion(ifMatch), idempotencyKey);
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedUser requireUser(AuthenticatedUser user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        return user;
    }

    private long parseVersion(String value) {
        try {
            return Long.parseLong(value.replace("\"", "").trim());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Invalid If-Match");
        }
    }

    private String etag(long version) {
        return "\"" + version + "\"";
    }

    public record RecipeRequest(
            @NotBlank String title,
            RecipeLanguage language,
            List<IngredientRequest> ingredients,
            List<StepRequest> steps) {
        RecipeCommand toCommand() {
            List<IngredientCommand> ingredientCommands = ingredients == null
                    ? List.of()
                    : ingredients.stream().map(IngredientRequest::toCommand).toList();
            List<StepCommand> stepCommands = steps == null
                    ? List.of()
                    : steps.stream().map(StepRequest::toCommand).toList();
            return new RecipeCommand(title, language, ingredientCommands, stepCommands);
        }

        RecipePatch toPatch() {
            List<IngredientCommand> ingredientCommands = ingredients == null
                    ? List.of()
                    : ingredients.stream().map(IngredientRequest::toCommand).toList();
            List<StepCommand> stepCommands = steps == null
                    ? List.of()
                    : steps.stream().map(StepRequest::toCommand).toList();
            return new RecipePatch(title, language, ingredientCommands, stepCommands);
        }
    }

    public record IngredientRequest(@NotBlank String name, BigDecimal amount, BigDecimal amountMax,
                                    String unit, String originalText) {
        IngredientCommand toCommand() {
            return new IngredientCommand(name, amount, amountMax, unit, originalText);
        }
    }

    public record StepRequest(@NotBlank String text) {
        StepCommand toCommand() {
            return new StepCommand(text);
        }
    }
}
