package com.reelcipe.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface ObjectStorage {
    PresignedUpload presignPut(String objectKey, String contentType, Duration duration);

    Optional<ObjectMetadata> head(String objectKey);

    InputStream open(String objectKey);

    void put(String objectKey, InputStream content, long sizeBytes, String contentType)
            throws IOException;

    void delete(String objectKey);

    record PresignedUpload(String url, Instant expiresAt) {
    }

    record ObjectMetadata(long sizeBytes, String contentType, String etag) {
    }
}
