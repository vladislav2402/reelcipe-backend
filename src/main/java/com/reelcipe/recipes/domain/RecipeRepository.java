package com.reelcipe.recipes.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT recipe FROM Recipe recipe WHERE recipe.id = :id AND recipe.userId = :userId AND recipe.deletedAt IS NULL")
    Optional<Recipe> findForUpdate(UUID id, UUID userId);

    Optional<Recipe> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
