package com.reelcipe.operations.deletion;

import com.reelcipe.operations.deletion.domain.DeletionTask;

public interface DeletionHandler {
    boolean supports(String resourceType);

    void delete(DeletionTask task);
}
