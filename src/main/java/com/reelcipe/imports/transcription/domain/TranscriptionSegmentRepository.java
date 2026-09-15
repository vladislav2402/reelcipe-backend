package com.reelcipe.imports.transcription.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TranscriptionSegmentRepository extends JpaRepository<TranscriptionSegment, UUID> {
    List<TranscriptionSegment> findByTranscriptionIdOrderBySegmentIndex(UUID transcriptionId);
}
