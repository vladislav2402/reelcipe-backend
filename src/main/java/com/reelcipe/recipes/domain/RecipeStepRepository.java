package com.reelcipe.recipes.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecipeStepRepository extends JpaRepository<RecipeStep, UUID> {
    List<RecipeStep> findByRecipeIdOrderByPosition(UUID recipeId);
    void deleteByRecipeId(UUID recipeId);
}
