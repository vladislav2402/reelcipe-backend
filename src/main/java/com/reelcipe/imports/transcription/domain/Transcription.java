package com.reelcipe.imports.transcription.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transcriptions")
public class Transcription {
    @Id
    private UUID id;
    @Column(name = "import_id", nullable = false)
    private UUID importId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "audio_asset_id", nullable = false)
    private UUID audioAssetId;
    @Column(nullable = false)
    private int version;
    @Column(name = "input_hash", nullable = false)
    private String inputHash;
    @Column(nullable = false)
    private String language;
    @Enumerated(EnumType.STRING)
    @Column(name = "speech_status", nullable = false)
    private SpeechStatus speechStatus;
    @Column(name = "transcript_hash", nullable = false)
    private String transcriptHash;
    @Column(name = "full_text", nullable = false)
    private String fullText;
    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;
    @Column(nullable = false)
    private String provider;
    @Column(nullable = false)
    private String model;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transcription() {
    }

    public Transcription(
            UUID id,
            UUID importId,
            UUID userId,
            UUID audioAssetId,
            int version,
            String inputHash,
            String language,
            SpeechStatus speechStatus,
            String transcriptHash,
            String fullText,
            int durationSeconds,
            String provider,
            String model,
            Instant now) {
        this.id = id;
        this.importId = importId;
        this.userId = userId;
        this.audioAssetId = audioAssetId;
        this.version = version;
        this.inputHash = inputHash;
        this.language = language;
        this.speechStatus = speechStatus;
        this.transcriptHash = transcriptHash;
        this.fullText = fullText;
        this.durationSeconds = durationSeconds;
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

    public UUID getAudioAssetId() {
        return audioAssetId;
    }

    public int getVersion() {
        return version;
    }

    public String getInputHash() {
        return inputHash;
    }

    public String getLanguage() {
        return language;
    }

    public SpeechStatus getSpeechStatus() {
        return speechStatus;
    }

    public String getTranscriptHash() {
        return transcriptHash;
    }

    public String getFullText() {
        return fullText;
    }
}
