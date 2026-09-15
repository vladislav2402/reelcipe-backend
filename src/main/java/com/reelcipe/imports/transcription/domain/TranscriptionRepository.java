package com.reelcipe.imports.transcription.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TranscriptionRepository extends JpaRepository<Transcription, UUID> {
    Optional<Transcription> findTopByImportIdAndAudioAssetIdOrderByVersionDesc(
            UUID importId,
            UUID audioAssetId);

    Optional<Transcription> findTopByImportIdOrderByVersionDesc(UUID importId);
}
