package com.reelcipe.imports.recipe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipe_candidates")
public class RecipeCandidate {
    @Id
    private UUID id;

    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "input_hash", nullable = false)
    private String inputHash;

    @Column(name = "transcription_id")
    private UUID transcriptionId;

    @Column(name = "transcription_version")
    private Integer transcriptionVersion;

    @Column(name = "schema_version", nullable = false)
    private String schemaVersion;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "pipeline_version", nullable = false)
    private String pipelineVersion;

    @Column(nullable = false)
    private String language;

    @Column(name = "source_coverage", nullable = false)
    private String sourceCoverage;

    @Column(name = "candidate_json", nullable = false, columnDefinition = "TEXT")
    private String candidateJson;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecipeCandidate() {
    }

    public RecipeCandidate(
            UUID id,
            UUID importId,
            UUID userId,
            String inputHash,
            UUID transcriptionId,
            Integer transcriptionVersion,
            String schemaVersion,
            String promptVersion,
            String pipelineVersion,
            String language,
            String sourceCoverage,
            String candidateJson,
            String provider,
            String model,
            Instant now) {
        this.id = id;
        this.importId = importId;
        this.userId = userId;
        this.inputHash = inputHash;
        this.transcriptionId = transcriptionId;
        this.transcriptionVersion = transcriptionVersion;
        this.schemaVersion = schemaVersion;
        this.promptVersion = promptVersion;
        this.pipelineVersion = pipelineVersion;
        this.language = language;
        this.sourceCoverage = sourceCoverage;
        this.candidateJson = candidateJson;
        this.provider = provider;
        this.model = model;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getImportId() {
        return importId;
    }

    public String getInputHash() {
        return inputHash;
    }

    public UUID getTranscriptionId() {
        return transcriptionId;
    }

    public Integer getTranscriptionVersion() {
        return transcriptionVersion;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getPipelineVersion() {
        return pipelineVersion;
    }

    public String getLanguage() {
        return language;
    }

    public String getSourceCoverage() {
        return sourceCoverage;
    }

    public String getCandidateJson() {
        return candidateJson;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }
}
