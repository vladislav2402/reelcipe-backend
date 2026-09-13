package com.reelcipe.recipes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "recipe_steps")
public class RecipeStep {
    @Id
    private UUID id;
    @Column(name = "recipe_id", nullable = false)
    private UUID recipeId;
    @Column(nullable = false)
    private int position;
    @Column(name = "text", nullable = false)
    private String text;

    protected RecipeStep() {
    }

    public RecipeStep(UUID id, UUID recipeId, int position, String text) {
        this.id = id;
        this.recipeId = recipeId;
        this.position = position;
        this.text = text;
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }
}
