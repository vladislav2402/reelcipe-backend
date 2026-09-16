package com.reelcipe.recipes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipe_evidence")
public class RecipeEvidence {
    @Id
    private UUID id;

    @Column(name = "recipe_id", nullable = false)
    private UUID recipeId;

    @Column(name = "ingredient_id")
    private UUID ingredientId;

    @Column(name = "step_id")
    private UUID stepId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "transcription_id")
    private UUID transcriptionId;

    @Column(name = "segment_id")
    private UUID segmentId;

    @Column(name = "segment_index")
    private Integer segmentIndex;

    @Column(name = "quote", nullable = false)
    private String quote;

    @Column(name = "start_ms")
    private Long startMs;

    @Column(name = "end_ms")
    private Long endMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RecipeEvidence() {
    }

    public RecipeEvidence(
            UUID id,
            UUID recipeId,
            UUID ingredientId,
            UUID stepId,
            String sourceType,
            UUID transcriptionId,
            UUID segmentId,
            Integer segmentIndex,
            String quote,
            Long startMs,
            Long endMs,
            Instant createdAt) {
        this.id = id;
        this.recipeId = recipeId;
        this.ingredientId = ingredientId;
        this.stepId = stepId;
        this.sourceType = sourceType;
        this.transcriptionId = transcriptionId;
        this.segmentId = segmentId;
        this.segmentIndex = segmentIndex;
        this.quote = quote;
        this.startMs = startMs;
        this.endMs = endMs;
        this.createdAt = createdAt;
    }
}
