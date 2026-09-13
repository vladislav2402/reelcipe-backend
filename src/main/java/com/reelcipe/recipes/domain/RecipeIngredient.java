package com.reelcipe.recipes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredient {
    @Id
    private UUID id;
    @Column(name = "recipe_id", nullable = false)
    private UUID recipeId;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false)
    private String name;
    private BigDecimal amount;
    @Column(name = "amount_max")
    private BigDecimal amountMax;
    private String unit;
    @Column(name = "original_text")
    private String originalText;

    protected RecipeIngredient() {
    }

    public RecipeIngredient(UUID id, UUID recipeId, int position, String name, BigDecimal amount, BigDecimal amountMax, String unit, String originalText) {
        this.id = id;
        this.recipeId = recipeId;
        this.position = position;
        this.name = name;
        this.amount = amount;
        this.amountMax = amountMax;
        this.unit = unit;
        this.originalText = originalText;
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getAmountMax() {
        return amountMax;
    }

    public String getUnit() {
        return unit;
    }

    public String getOriginalText() {
        return originalText;
    }
}
