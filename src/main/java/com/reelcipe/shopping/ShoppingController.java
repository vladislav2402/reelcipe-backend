package com.reelcipe.shopping;

import com.reelcipe.auth.domain.AuthenticatedUser;
import com.reelcipe.shopping.ShoppingService.ItemCommand;
import com.reelcipe.shopping.ShoppingService.ItemPatch;
import com.reelcipe.shopping.ShoppingService.RecipeAddition;
import com.reelcipe.shopping.ShoppingService.ShoppingView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/v1/shopping-list")
@Profile("!worker")
@SecurityRequirement(name = "bearerAuth")
public class ShoppingController {
    private final ShoppingService service;

    public ShoppingController(ShoppingService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get the current shopping list")
    public ResponseEntity<ShoppingView> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.get(requireUser(user).userId()));
    }

    @PostMapping("/from-recipe")
    @Operation(summary = "Add a recipe ingredient snapshot to the shopping list")
    public ResponseEntity<ShoppingView> addRecipe(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RecipeAdditionRequest request) {
        ShoppingView view = service.addRecipe(
                requireUser(user).userId(),
                idempotencyKey,
                request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @PostMapping("/items")
    @Operation(summary = "Add a manual shopping item")
    public ResponseEntity<ShoppingView> addItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addItem(
                requireUser(user).userId(), idempotencyKey, request.toCommand()));
    }

    @PatchMapping("/items/{itemId}")
    @Operation(summary = "Update a shopping item")
    public ResponseEntity<ShoppingView> patchItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itemId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ItemPatchRequest request) {
        return ResponseEntity.ok(service.patchItem(
                requireUser(user).userId(),
                itemId,
                parseVersion(ifMatch),
                idempotencyKey,
                request.toCommand()));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Delete a shopping item")
    public ResponseEntity<Void> deleteItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itemId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        service.deleteItem(requireUser(user).userId(), itemId, parseVersion(ifMatch), idempotencyKey);
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedUser requireUser(AuthenticatedUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return user;
    }

    private long parseVersion(String value) {
        try {
            return Long.parseLong(value.replace("\"", "").trim());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Invalid If-Match");
        }
    }

    public record RecipeAdditionRequest(
            @NotNull UUID recipeId,
            @Positive long recipeVersion,
            @NotNull UUID additionId) {
        RecipeAddition toCommand() {
            return new RecipeAddition(recipeId, recipeVersion, additionId);
        }
    }

    public record ItemRequest(
            @NotBlank String name,
            BigDecimal amount,
            String unit) {
        ItemCommand toCommand() {
            return new ItemCommand(name, amount, unit);
        }
    }

    public record ItemPatchRequest(String name, BigDecimal amount, String unit, Boolean checked) {
        ItemPatch toCommand() {
            return new ItemPatch(name, amount, unit, checked);
        }
    }
}
