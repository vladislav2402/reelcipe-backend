package com.reelcipe.operations.deletion;

import com.reelcipe.operations.deletion.domain.DeletionTask;
import com.reelcipe.storage.ObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ObjectStorageDeletionHandler implements DeletionHandler {
    private final ObjectStorage storage;

    public ObjectStorageDeletionHandler(ObjectStorage storage) {
        this.storage = storage;
    }

    @Override
    public boolean supports(String resourceType) {
        return "PROCESSING_SOURCE".equals(resourceType)
                || "STAGING_OBJECT".equals(resourceType)
                || "NORMALIZED_AUDIO".equals(resourceType);
    }

    @Override
    public void delete(DeletionTask task) {
        storage.delete(task.getResourceKey());
    }
}
