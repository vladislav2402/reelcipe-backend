package com.reelcipe.imports;

import com.reelcipe.imports.domain.*;
import com.reelcipe.operations.deletion.DeletionTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AudioCheckpointPersistence {
    private final ImportJobRepository jobs;
    private final MediaAssetRepository assets;
    private final DeletionTaskService deletions;
    private final Clock clock;

    public AudioCheckpointPersistence(
            ImportJobRepository jobs,
            MediaAssetRepository assets,
            DeletionTaskService deletions,
            Clock clock) {
        this.jobs = jobs;
        this.assets = assets;
        this.deletions = deletions;
        this.clock = clock;
    }

    @Transactional
    public void checkpoint(
            ImportLease lease,
            UUID audioAssetId,
            String processingKey,
            String sha256,
            long sizeBytes,
            int durationSeconds) {
        ImportJob job = fencedJob(lease);
        MediaAsset existing = assets.findLockedByImportIdAndAssetType(
                        lease.importId(), MediaAssetType.NORMALIZED_AUDIO)
                .orElse(null);
        if (existing == null) {
            existing = MediaAsset.normalizedAudio(
                    audioAssetId,
                    lease.importId(),
                    lease.userId(),
                    processingKey,
                    sha256,
                    sizeBytes,
                    durationSeconds,
                    job.getProcessingDeadlineAt(),
                    clock.instant());
        } else if (!processingKey.equals(existing.getProcessingKey())
                || !sha256.equals(existing.getSha256())
                || sizeBytes != existing.getSizeBytes()) {
            throw new IllegalStateException("Audio checkpoint is already immutable");
        }
        assets.save(existing);
        job.recordAudioOutcome(AudioOutcome.AUDIO_READY, clock);
        job.checkpoint(lease.stage(), processingKey, clock);
        job.completeStage(lease.stage(), ImportStatus.TRANSCRIBING, clock);
        jobs.save(job);
        enqueueSourceDeletion(job, lease, sourceAsset(lease.importId()));
    }

    @Transactional
    public void descriptionOnly(ImportLease lease) {
        ImportJob job = fencedJob(lease);
        job.recordAudioOutcome(AudioOutcome.DESCRIPTION_ONLY, clock);
        job.completeStage(lease.stage(), ImportStatus.EXTRACTING_RECIPE, clock);
        jobs.save(job);
        enqueueSourceDeletion(job, lease, sourceAsset(lease.importId()));
    }

    private ImportJob fencedJob(ImportLease lease) {
        return jobs.findFencedForUpdate(
                        lease.importId(),
                        lease.owner(),
                        lease.leaseVersion(),
                        lease.inputRevision())
                .orElseThrow(() -> new LeaseLostException(lease));
    }

    private MediaAsset sourceAsset(UUID importId) {
        return assets.findByImportIdAndAssetType(importId, MediaAssetType.SOURCE)
                .orElse(null);
    }

    private void enqueueSourceDeletion(
            ImportJob job,
            ImportLease lease,
            MediaAsset source) {
        if (source == null || source.getProcessingKey() == null) {
            return;
        }
        deletions.enqueue(
                job.getUserId(),
                "PROCESSING_SOURCE",
                source.getProcessingKey(),
                "source-processing:" + lease.importId());
    }
}
