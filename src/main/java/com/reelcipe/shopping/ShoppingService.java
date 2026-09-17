package com.reelcipe.shopping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.common.UuidV7;
import com.reelcipe.idempotency.IdempotencyService;
import com.reelcipe.idempotency.domain.IdempotencyResult;
import com.reelcipe.recipes.domain.Recipe;
import com.reelcipe.recipes.domain.RecipeIngredient;
import com.reelcipe.recipes.domain.RecipeIngredientRepository;
import com.reelcipe.recipes.domain.RecipeRepository;
import com.reelcipe.shopping.domain.*;
import com.reelcipe.sync.SyncChangeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ShoppingService {
    private final ShoppingListRepository lists;
    private final ShoppingItemRepository items;
    private final ShoppingItemSourceRepository sources;
    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final IdempotencyService idempotency;
    private final SyncChangeService sync;
    private final ObjectMapper objectMapper;

    public ShoppingService(
            ShoppingListRepository lists,
            ShoppingItemRepository items,
            ShoppingItemSourceRepository sources,
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            IdempotencyService idempotency,
            SyncChangeService sync,
            ObjectMapper objectMapper) {
        this.lists = lists;
        this.items = items;
        this.sources = sources;
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.idempotency = idempotency;
        this.sync = sync;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ShoppingView get(UUID userId) {
        return view(getOrCreateList(userId));
    }

    @Transactional
    public ShoppingView addRecipe(UUID userId, String key, RecipeAddition command) {
        getOrCreateList(userId);
        ShoppingList list = lockedList(userId);
        String body = json(command);
        IdempotencyResult result = idempotency.execute(
                userId,
                "shopping.recipe.add",
                list.getId().toString(),
                key,
                body,
                () -> IdempotencyResult.created(json(addRecipeSnapshot(userId, list, command))));
        return readJson(result.body(), ShoppingView.class);
    }

    @Transactional
    public ShoppingView addItem(UUID userId, String key, ItemCommand command) {
        getOrCreateList(userId);
        ShoppingList list = lockedList(userId);
        IdempotencyResult result = idempotency.execute(
                userId,
                "shopping.item.create",
                list.getId().toString(),
                key,
                json(command),
                () -> IdempotencyResult.created(json(createItem(userId, list, command))));
        return readJson(result.body(), ShoppingView.class);
    }

    @Transactional
    public ShoppingView patchItem(UUID userId, UUID itemId, long expectedVersion, String key, ItemPatch command) {
        ShoppingList list = lockedList(userId);
        IdempotencyResult result = idempotency.execute(
                userId,
                "shopping.item.update",
                itemId.toString(),
                key,
                json(command),
                () -> IdempotencyResult.ok(json(updateItem(userId, list, itemId, expectedVersion, command))));
        return readJson(result.body(), ShoppingView.class);
    }

    @Transactional
    public void deleteItem(UUID userId, UUID itemId, long expectedVersion, String key) {
        ShoppingList list = lockedList(userId);
        idempotency.execute(
                userId,
                "shopping.item.delete",
                itemId.toString(),
                key,
                "delete",
                () -> IdempotencyResult.ok(json(deleteItem(userId, list, itemId, expectedVersion))));
    }

    private ShoppingView addRecipeSnapshot(UUID userId, ShoppingList list, RecipeAddition command) {
        List<ShoppingItemSource> existing = sources.findByListIdAndAdditionId(list.getId(), command.additionId());
        if (!existing.isEmpty()) {
            return view(list);
        }
        Recipe recipe = recipes.findByIdAndUserIdAndDeletedAtIsNull(command.recipeId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found"));
        if (recipe.getVersion() != command.recipeVersion()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Recipe version does not match");
        }
        List<RecipeIngredient> recipeIngredients = ingredients.findByRecipeIdOrderByPosition(recipe.getId());
        for (RecipeIngredient ingredient : recipeIngredients) {
            ShoppingItem item = new ShoppingItem(
                    UuidV7.randomUuid(),
                    list.getId(),
                    ingredient.getName(),
                    ingredient.getAmount(),
                    ingredient.getUnit(),
                    Instant.now());
            items.save(item);
            sources.save(new ShoppingItemSource(
                    UuidV7.randomUuid(),
                    list.getId(),
                    item.getId(),
                    recipe.getId(),
                    recipe.getVersion(),
                    ingredient.getId(),
                    command.additionId()));
            sync.record(userId, "shopping_item", item.getId(), "CREATE", item.getVersion());
        }
        list.touch(Instant.now());
        lists.save(list);
        return view(list);
    }

    private ShoppingView createItem(UUID userId, ShoppingList list, ItemCommand command) {
        ShoppingItem item = new ShoppingItem(
                UuidV7.randomUuid(), list.getId(), command.name(), command.amount(), command.unit(), Instant.now());
        items.save(item);
        list.touch(Instant.now());
        lists.save(list);
        sync.record(userId, "shopping_item", item.getId(), "CREATE", item.getVersion());
        return view(list);
    }

    private ShoppingView updateItem(
            UUID userId,
            ShoppingList list,
            UUID itemId,
            long expectedVersion,
            ItemPatch command) {
        ShoppingItem item = items.findByIdAndListIdAndDeletedAtIsNull(itemId, list.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping item not found"));
        ensureVersion(item.getVersion(), expectedVersion);
        item.update(command.name(), command.amount(), command.unit(), command.checked(), Instant.now());
        items.save(item);
        list.touch(Instant.now());
        lists.save(list);
        sync.record(userId, "shopping_item", item.getId(), "UPDATE", item.getVersion());
        return view(list);
    }

    private ShoppingView deleteItem(UUID userId, ShoppingList list, UUID itemId, long expectedVersion) {
        ShoppingItem item = items.findByIdAndListIdAndDeletedAtIsNull(itemId, list.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping item not found"));
        ensureVersion(item.getVersion(), expectedVersion);
        item.delete(Instant.now());
        items.save(item);
        list.touch(Instant.now());
        lists.save(list);
        sync.record(userId, "shopping_item", item.getId(), "DELETE", item.getVersion());
        return view(list);
    }

    private ShoppingList getOrCreateList(UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return lists.findByUserId(userId).orElseGet(() -> lists.save(new ShoppingList(
                UuidV7.randomUuid(), userId, Instant.now())));
    }

    private ShoppingList lockedList(UUID userId) {
        return lists.findLockedByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping list not found"));
    }

    private void ensureVersion(long actual, long expected) {
        if (actual != expected) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Shopping item version does not match");
        }
    }

    private ShoppingView view(ShoppingList list) {
        List<ItemView> itemViews = items.findByListIdAndDeletedAtIsNullOrderByCreatedAtAsc(list.getId()).stream()
                .map(item -> new ItemView(item.getId(), item.getName(), item.getAmount(), item.getUnit(),
                        item.isChecked(), item.getVersion(), item.getCreatedAt(), item.getUpdatedAt()))
                .toList();
        return new ShoppingView(list.getId(), itemViews);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record RecipeAddition(UUID recipeId, long recipeVersion, UUID additionId) {
    }

    public record ItemCommand(String name, BigDecimal amount, String unit) {
    }

    public record ItemPatch(String name, BigDecimal amount, String unit, Boolean checked) {
    }

    public record ShoppingView(UUID id, List<ItemView> items) {
    }

    public record ItemView(UUID id, String name, BigDecimal amount, String unit, boolean checked,
                           long version, Instant createdAt, Instant updatedAt) {
    }
}
