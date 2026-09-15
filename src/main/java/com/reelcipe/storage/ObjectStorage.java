package com.reelcipe.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface ObjectStorage {
    PresignedUpload presignPut(String objectKey, String contentType, Duration duration);

    Optional<ObjectMetadata> head(String objectKey);

    void delete(String objectKey);

    record PresignedUpload(String url, Instant expiresAt) {
    }

    record ObjectMetadata(long sizeBytes, String contentType, String etag) {
    }
}
