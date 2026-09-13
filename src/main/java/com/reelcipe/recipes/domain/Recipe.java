package com.reelcipe.recipes.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipes")
public class Recipe {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private String title;
    @Enumerated(EnumType.STRING)
    private RecipeLanguage language;
    @Column(name = "analysis_mode", nullable = false)
    @Enumerated(EnumType.STRING)
    private RecipeAnalysisMode analysisMode;
    @Column(name = "library_state", nullable = false)
    @Enumerated(EnumType.STRING)
    private RecipeLibraryState libraryState;
    @Column(nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Column(name = "preview_media_id")
    private UUID previewMediaId;

    protected Recipe() {
    }

    public Recipe(UUID id, UUID userId, String title, RecipeLanguage language, UUID previewMediaId,
                  RecipeAnalysisMode analysisMode, RecipeLibraryState libraryState, Instant now) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.language = language;
        this.previewMediaId = previewMediaId;
        this.analysisMode = analysisMode;
        this.libraryState = libraryState;
        this.version = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, RecipeLanguage language, Instant now) {
        this.title = title;
        this.language = language;
        this.version++;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
        this.libraryState = RecipeLibraryState.DELETED;
        this.version++;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public RecipeLanguage getLanguage() {
        return language;
    }

    public RecipeLibraryState getLibraryState() {
        return libraryState;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UUID getPreviewMediaId() {
        return previewMediaId;
    }
}
