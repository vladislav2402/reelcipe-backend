package com.reelcipe.recipes.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecipeImportResultRepository extends JpaRepository<RecipeImportResult, UUID> {
    Optional<RecipeImportResult> findByImportId(UUID importId);
}
