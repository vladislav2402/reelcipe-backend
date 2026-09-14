package com.reelcipe.imports.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    Optional<ImportJob> findByIdAndUserId(UUID id, UUID userId);

    Optional<ImportJob> findByUserIdAndClientRequestId(UUID userId, UUID clientRequestId);

    List<ImportJob> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);
}
