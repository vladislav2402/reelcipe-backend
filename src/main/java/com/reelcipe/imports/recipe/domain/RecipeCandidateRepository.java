package com.reelcipe.imports.recipe.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecipeCandidateRepository extends JpaRepository<RecipeCandidate, UUID> {
    Optional<RecipeCandidate> findTopByImportIdAndInputHashOrderByCreatedAtDesc(
            UUID importId,
            String inputHash);
}
