package com.reelcipe.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "memory")
public class InMemoryObjectStorage implements ObjectStorage {
    private final Map<String, ObjectMetadata> objects = new ConcurrentHashMap<>();

    @Override
    public PresignedUpload presignPut(String objectKey, String contentType, Duration duration) {
        return new PresignedUpload(
                "http://memory.invalid/upload/" + objectKey,
                Instant.now().plus(duration));
    }

    @Override
    public Optional<ObjectMetadata> head(String objectKey) {
        return Optional.ofNullable(objects.get(objectKey));
    }

    @Override
    public void delete(String objectKey) {
        objects.remove(objectKey);
    }

    public void put(String objectKey, long sizeBytes, String contentType) {
        objects.put(objectKey, new ObjectMetadata(sizeBytes, contentType, "memory-etag"));
    }
}
