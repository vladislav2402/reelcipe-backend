package com.reelcipe.recipes.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, UUID> {
    List<RecipeIngredient> findByRecipeIdOrderByPosition(UUID recipeId);
    void deleteByRecipeId(UUID recipeId);
}
