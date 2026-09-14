package com.reelcipe.shopping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.idempotency.IdempotencyService;
import com.reelcipe.recipes.domain.RecipeIngredientRepository;
import com.reelcipe.recipes.domain.RecipeRepository;
import com.reelcipe.shopping.domain.*;
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
class ShoppingServiceTest {
    @Mock
    ShoppingListRepository lists;
    @Mock
    ShoppingItemRepository items;
    @Mock
    ShoppingItemSourceRepository sources;
    @Mock
    RecipeRepository recipes;
    @Mock
    RecipeIngredientRepository ingredients;
    @Mock
    IdempotencyService idempotency;
    @Mock
    SyncChangeService sync;

    @Test
    void patchRejectsStaleIfMatchVersion() {
        ShoppingService service = new ShoppingService(
                lists, items, sources, recipes, ingredients, idempotency, sync, new ObjectMapper());
        UUID userId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ShoppingList list = new ShoppingList(listId, userId, Instant.now());
        ShoppingItem item = new ShoppingItem(itemId, listId, "Flour", null, "g", Instant.now());
        when(lists.findLockedByUserId(userId)).thenReturn(Optional.of(list));
        when(items.findByIdAndListIdAndDeletedAtIsNull(itemId, listId)).thenReturn(Optional.of(item));
        when(idempotency.execute(any(), any(), any(), any(), any(), any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(5)).get());

        assertThrows(ResponseStatusException.class, () -> service.patchItem(
                userId,
                itemId,
                2,
                "key",
                new ShoppingService.ItemPatch(null, null, null, true)));
    }
}
