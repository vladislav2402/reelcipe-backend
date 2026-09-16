package com.reelcipe.recipes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipe_imports")
public class RecipeImportResult {
    @Id
    private UUID id;

    @Column(name = "recipe_id", nullable = false)
    private UUID recipeId;

    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source_coverage", nullable = false)
    private String sourceCoverage;

    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RecipeImportResult() {
    }

    public RecipeImportResult(
            UUID id,
            UUID recipeId,
            UUID importId,
            UUID userId,
            String sourceCoverage,
            boolean reviewRequired,
            Instant createdAt) {
        this.id = id;
        this.recipeId = recipeId;
        this.importId = importId;
        this.userId = userId;
        this.sourceCoverage = sourceCoverage;
        this.reviewRequired = reviewRequired;
        this.createdAt = createdAt;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public UUID getImportId() {
        return importId;
    }

    public boolean isReviewRequired() {
        return reviewRequired;
    }
}
