package com.reelcipe.imports.transcription.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiAttemptRepository extends JpaRepository<AiAttempt, UUID> {
    Optional<AiAttempt> findTopByImportIdAndKindOrderByAttemptNumberDesc(
            UUID importId,
            AiAttemptKind kind);
}
