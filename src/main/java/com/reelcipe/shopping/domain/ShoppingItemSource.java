package com.reelcipe.shopping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "shopping_item_sources")
public class ShoppingItemSource {
    @Id
    private UUID id;
    @Column(name = "list_id")
    private UUID listId;
    @Column(name = "item_id")
    private UUID itemId;
    @Column(name = "recipe_id")
    private UUID recipeId;
    @Column(name = "recipe_version")
    private Long recipeVersion;
    @Column(name = "ingredient_id")
    private UUID ingredientId;
    @Column(name = "addition_id")
    private UUID additionId;

    protected ShoppingItemSource() {
    }

    public ShoppingItemSource(UUID id, UUID listId, UUID itemId, UUID recipeId, Long recipeVersion,
                              UUID ingredientId, UUID additionId) {
        this.id = id;
        this.listId = listId;
        this.itemId = itemId;
        this.recipeId = recipeId;
        this.recipeVersion = recipeVersion;
        this.ingredientId = ingredientId;
        this.additionId = additionId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public UUID getAdditionId() {
        return additionId;
    }
}
