package com.reelcipe.imports.domain;

import java.time.Instant;
import java.util.UUID;

public record ImportLease(
        UUID importId,
        UUID userId,
        String owner,
        long leaseVersion,
        long inputRevision,
        ImportStage stage,
        Instant leaseUntil) {

    public static ImportLease from(ImportJob job) {
        return new ImportLease(
                job.getId(),
                job.getUserId(),
                job.getLeaseOwner(),
                job.getLeaseVersion(),
                job.getInputRevision(),
                ImportStage.valueOf(job.getStatus().name()),
                job.getLeaseUntil());
    }
}
