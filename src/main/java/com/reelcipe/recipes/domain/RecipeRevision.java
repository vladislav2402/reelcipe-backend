package com.reelcipe.recipes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipe_revisions")
public class RecipeRevision {
    @Id
    private UUID id;
    @Column(name = "recipe_id", nullable = false)
    private UUID recipeId;
    @Column(nullable = false)
    private long version;
    @Column(name = "edited_by", nullable = false)
    private UUID editedBy;
    @Column(nullable = false)
    private String snapshot;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RecipeRevision() {
    }

    public RecipeRevision(UUID recipeId, long version, UUID editedBy, String snapshot, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.recipeId = recipeId;
        this.version = version;
        this.editedBy = editedBy;
        this.snapshot = snapshot;
        this.createdAt = createdAt;
    }
}
