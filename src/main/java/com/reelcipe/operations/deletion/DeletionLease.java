package com.reelcipe.operations.deletion;

import com.reelcipe.operations.deletion.domain.DeletionTask;

import java.util.UUID;

public record DeletionLease(
        UUID id,
        String owner,
        long version,
        DeletionTask task) {
}
