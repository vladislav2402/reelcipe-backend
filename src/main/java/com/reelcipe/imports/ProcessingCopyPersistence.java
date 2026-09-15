package com.reelcipe.imports;

import com.reelcipe.imports.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ProcessingCopyPersistence {
    private final ImportJobRepository jobs;
    private final MediaAssetRepository assets;
    private final Clock clock;

    public ProcessingCopyPersistence(
            ImportJobRepository jobs,
            MediaAssetRepository assets,
            Clock clock) {
        this.jobs = jobs;
        this.assets = assets;
        this.clock = clock;
    }

    @Transactional
    public void checkpoint(
            ImportLease lease,
            String processingKey,
            String sha256,
            long sizeBytes) {
        ImportJob job = jobs.findFencedForUpdate(
                        lease.importId(),
                        lease.owner(),
                        lease.leaseVersion(),
                        lease.inputRevision())
                .orElseThrow(() -> new LeaseLostException(lease));
        MediaAsset asset = assets.findLockedByImportIdAndAssetType(
                        lease.importId(), MediaAssetType.SOURCE)
                .orElseThrow(() -> new IllegalStateException("Source media asset was not found"));
        asset.recordProcessingCopy(processingKey, sha256, sizeBytes, clock);
        job.checkpoint(lease.stage(), processingKey, clock);
        job.completeStage(lease.stage(), ImportStatus.EXTRACTING_AUDIO, clock);
        assets.save(asset);
        jobs.save(job);
    }
}
