package com.reelcipe.operations.deletion.domain;

import java.util.UUID;

public class DeletionLeaseLostException extends RuntimeException {
    public DeletionLeaseLostException(UUID taskId) {
        super("Deletion task lease was lost: " + taskId);
    }
}
