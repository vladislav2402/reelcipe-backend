package com.reelcipe.recipes.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT recipe FROM Recipe recipe WHERE recipe.id = :id AND recipe.userId = :userId AND recipe.deletedAt IS NULL")
    Optional<Recipe> findForUpdate(UUID id, UUID userId);

    Optional<Recipe> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Query("""
            SELECT recipe
            FROM Recipe recipe
            WHERE recipe.userId = :userId
              AND recipe.libraryState = :state
              AND recipe.deletedAt IS NULL
              AND (:search IS NULL
                   OR LOWER(recipe.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR EXISTS (
                       SELECT ingredient.id
                       FROM RecipeIngredient ingredient
                       WHERE ingredient.recipeId = recipe.id
                         AND LOWER(ingredient.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   ))
            ORDER BY recipe.updatedAt DESC, recipe.id DESC
            """)
    List<Recipe> findLibrary(
            UUID userId,
            RecipeLibraryState state,
            String search,
            Pageable pageable);

    @Query("""
            SELECT recipe
            FROM Recipe recipe
            WHERE recipe.userId = :userId
              AND recipe.libraryState = :state
              AND recipe.deletedAt IS NULL
              AND (:search IS NULL
                   OR LOWER(recipe.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR EXISTS (
                       SELECT ingredient.id
                       FROM RecipeIngredient ingredient
                       WHERE ingredient.recipeId = recipe.id
                         AND LOWER(ingredient.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   ))
              AND (recipe.updatedAt < :cursorUpdatedAt
                   OR (recipe.updatedAt = :cursorUpdatedAt AND recipe.id < :cursorId))
            ORDER BY recipe.updatedAt DESC, recipe.id DESC
            """)
    List<Recipe> findLibraryAfter(
            UUID userId,
            RecipeLibraryState state,
            String search,
            Instant cursorUpdatedAt,
            UUID cursorId,
            Pageable pageable);
}
