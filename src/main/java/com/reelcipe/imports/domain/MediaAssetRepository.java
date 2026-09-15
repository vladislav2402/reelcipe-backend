package com.reelcipe.imports.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    Optional<MediaAsset> findByUploadAttemptId(UUID uploadAttemptId);

    Optional<MediaAsset> findByImportIdAndAssetType(UUID importId, MediaAssetType assetType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT asset
            FROM MediaAsset asset
            WHERE asset.importId = :importId
              AND asset.assetType = :assetType
            """)
    Optional<MediaAsset> findLockedByImportIdAndAssetType(
            @Param("importId") UUID importId,
            @Param("assetType") MediaAssetType assetType);
}
