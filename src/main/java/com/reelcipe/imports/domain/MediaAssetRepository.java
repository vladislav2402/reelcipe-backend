package com.reelcipe.imports.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    Optional<MediaAsset> findByUploadAttemptId(UUID uploadAttemptId);

    Optional<MediaAsset> findByImportIdAndAssetType(UUID importId, MediaAssetType assetType);
}
