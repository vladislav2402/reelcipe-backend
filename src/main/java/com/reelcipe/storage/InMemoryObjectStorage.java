package com.reelcipe.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "memory")
public class InMemoryObjectStorage implements ObjectStorage {
    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

    @Override
    public PresignedUpload presignPut(String objectKey, String contentType, Duration duration) {
        return new PresignedUpload(
                "http://memory.invalid/upload/" + objectKey,
                Instant.now().plus(duration));
    }

    @Override
    public Optional<ObjectMetadata> head(String objectKey) {
        return Optional.ofNullable(objects.get(objectKey)).map(StoredObject::metadata);
    }

    @Override
    public InputStream open(String objectKey) {
        StoredObject object = objects.get(objectKey);
        if (object == null) {
            throw new IllegalArgumentException("Object was not found: " + objectKey);
        }
        return new ByteArrayInputStream(object.content());
    }

    @Override
    public void put(
            String objectKey,
            InputStream content,
            long sizeBytes,
            String contentType) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        content.transferTo(output);
        byte[] bytes = output.toByteArray();
        if (bytes.length != sizeBytes) {
            throw new IOException("Object size does not match the declared size");
        }
        putBytes(objectKey, bytes, contentType);
    }

    @Override
    public void delete(String objectKey) {
        objects.remove(objectKey);
    }

    public void put(String objectKey, long sizeBytes, String contentType) {
        if (sizeBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Memory object is too large");
        }
        putBytes(objectKey, new byte[(int) sizeBytes], contentType);
    }

    public void putBytes(String objectKey, byte[] content, String contentType) {
        byte[] copy = Arrays.copyOf(content, content.length);
        ObjectMetadata metadata = new ObjectMetadata(
                copy.length, contentType, "memory-etag-" + Integer.toHexString(Arrays.hashCode(copy)));
        objects.put(objectKey, new StoredObject(copy, metadata));
    }

    public byte[] bytes(String objectKey) {
        StoredObject object = objects.get(objectKey);
        if (object == null) {
            throw new IllegalArgumentException("Object was not found: " + objectKey);
        }
        return Arrays.copyOf(object.content(), object.content().length);
    }

    private record StoredObject(byte[] content, ObjectMetadata metadata) {
    }
}
