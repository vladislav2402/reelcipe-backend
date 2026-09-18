package com.reelcipe.imports.source;

import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class SourceResolutionPersistence {
    private final ImportJobRepository jobs;
    private final MediaAssetRepository assets;
    private final Clock clock;

    public SourceResolutionPersistence(
            ImportJobRepository jobs,
            MediaAssetRepository assets,
            Clock clock) {
        this.jobs = jobs;
        this.assets = assets;
        this.clock = clock;
    }

    @Transactional
    public void media(
            ImportLease lease,
            String processingKey,
            String sha256,
            long sizeBytes,
            String contentType,
            SourceMetadata metadata) {
        ImportJob job = fencedJob(lease);
        MediaAsset asset = assets.findLockedByImportIdAndAssetType(
                        lease.importId(), MediaAssetType.SOURCE)
                .orElseGet(() -> new MediaAsset(
                        UuidV7.randomUuid(),
                        lease.importId(),
                        lease.userId(),
                        null,
                        MediaAssetType.SOURCE,
                        null,
                        sizeBytes,
                        contentType,
                        job.getProcessingDeadlineAt(),
                        clock.instant()));
        asset.recordProcessingCopy(processingKey, sha256, sizeBytes, clock);
        job.recordResolvedMedia(contentType, sizeBytes, clock);
        recordMetadata(job, metadata);
        job.checkpoint(ImportStage.RESOLVING, processingKey, clock);
        job.completeStage(ImportStage.RESOLVING, ImportStatus.EXTRACTING_AUDIO, clock);
        assets.save(asset);
        jobs.save(job);
    }

    @Transactional
    public void descriptionOnly(ImportLease lease, SourceMetadata metadata) {
        ImportJob job = fencedJob(lease);
        recordMetadata(job, metadata);
        job.completeStage(ImportStage.RESOLVING, ImportStatus.EXTRACTING_AUDIO, clock);
        job.recordAudioOutcome(AudioOutcome.DESCRIPTION_ONLY, clock);
        job.completeStage(ImportStage.EXTRACTING_AUDIO, ImportStatus.EXTRACTING_RECIPE, clock);
        jobs.save(job);
    }

    private void recordMetadata(ImportJob job, SourceMetadata metadata) {
        job.recordSourceMetadata(
                metadata.canonicalUrl() == null ? null : metadata.canonicalUrl().toString(),
                metadata.platform(),
                metadata.authorName(),
                metadata.authorUrl(),
                metadata.authorDescription(),
                metadata.userText(),
                metadata.availability(),
                metadata.descriptionAvailability(),
                metadata.audioAvailability(),
                metadata.transcript(),
                metadata.transcriptLanguage(),
                metadata.transcriptProvider(),
                clock);
    }

    private ImportJob fencedJob(ImportLease lease) {
        return jobs.findFencedForUpdate(
                        lease.importId(),
                        lease.owner(),
                        lease.leaseVersion(),
                        lease.inputRevision())
                .orElseThrow(() -> new LeaseLostException(lease));
    }
}
