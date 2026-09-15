package com.reelcipe.imports.transcription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "transcription_segments")
public class TranscriptionSegment {
    @Id
    private UUID id;
    @Column(name = "transcription_id", nullable = false)
    private UUID transcriptionId;
    @Column(name = "segment_index", nullable = false)
    private int segmentIndex;
    @Column(name = "start_ms", nullable = false)
    private long startMs;
    @Column(name = "end_ms", nullable = false)
    private long endMs;
    @Column(nullable = false)
    private String text;

    protected TranscriptionSegment() {
    }

    public TranscriptionSegment(
            UUID id,
            UUID transcriptionId,
            int segmentIndex,
            long startMs,
            long endMs,
            String text) {
        this.id = id;
        this.transcriptionId = transcriptionId;
        this.segmentIndex = segmentIndex;
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTranscriptionId() {
        return transcriptionId;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public String getText() {
        return text;
    }
}
