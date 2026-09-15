package com.reelcipe.imports;

import com.reelcipe.imports.domain.*;
import com.reelcipe.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessingCopyServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path tempDirectory;

    @Test
    void streamsSourceComputesHashAndPersistsServerOwnedCopy() throws Exception {
        byte[] content = "source-bytes".getBytes(StandardCharsets.UTF_8);
        UUID importId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = sourceAsset(importId, assetId, content.length);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        ProcessingCopyPersistence persistence = mock(ProcessingCopyPersistence.class);
        when(assets.findByImportIdAndAssetType(importId, MediaAssetType.SOURCE))
                .thenReturn(Optional.of(asset));
        when(storage.head("staging/source"))
                .thenReturn(Optional.of(metadata(content.length, "etag-1")))
                .thenReturn(Optional.of(metadata(content.length, "etag-1")));
        when(storage.open("staging/source"))
                .thenReturn(new ByteArrayInputStream(content));

        byte[][] uploaded = new byte[1][];
        doAnswer(invocation -> {
            try (InputStream input = invocation.getArgument(1)) {
                uploaded[0] = input.readAllBytes();
            }
            return null;
        }).when(storage).put(any(), any(), eq((long) content.length), eq("video/mp4"));

        ImportLease lease = lease(importId, assetId, 3);
        ProcessingCopyService service = service(assets, storage, persistence, 1024);
        service.copy(lease, new ImportLeaseControl());

        assertThat(uploaded[0]).containsExactly(content);
        verify(persistence).checkpoint(
                eq(lease),
                eq("processing/" + lease.userId() + "/" + importId + "/" + assetId + "/lease-3/source"),
                eq(sha256(content)),
                eq((long) content.length));
    }

    @Test
    void doesNotReadStagingAgainWhenProcessingCopyIsAlreadyCommitted() throws Exception {
        UUID importId = UUID.randomUUID();
        MediaAsset asset = sourceAsset(importId, UUID.randomUUID(), 4);
        asset.recordProcessingCopy("processing/existing", "hash", 4, CLOCK);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        ProcessingCopyPersistence persistence = mock(ProcessingCopyPersistence.class);
        when(assets.findByImportIdAndAssetType(importId, MediaAssetType.SOURCE))
                .thenReturn(Optional.of(asset));

        ImportLease lease = lease(importId, asset.getId(), 4);
        service(assets, storage, persistence, 1024).copy(lease, new ImportLeaseControl());

        verify(persistence, never()).checkpoint(any(), any(), any(), anyLong());
        verify(storage, never()).head(any());
        verify(storage, never()).open(any());
        verify(storage, never()).put(any(), any(), anyLong(), any());
    }

    @Test
    void keepsProcessingObjectWhenFencedCheckpointRejectsStaleLease() throws Exception {
        byte[] content = "source".getBytes(StandardCharsets.UTF_8);
        UUID importId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = sourceAsset(importId, assetId, content.length);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        ProcessingCopyPersistence persistence = mock(ProcessingCopyPersistence.class);
        when(assets.findByImportIdAndAssetType(importId, MediaAssetType.SOURCE))
                .thenReturn(Optional.of(asset));
        when(storage.head("staging/source"))
                .thenReturn(Optional.of(metadata(content.length, "etag-1")))
                .thenReturn(Optional.of(metadata(content.length, "etag-1")));
        when(storage.open("staging/source"))
                .thenReturn(new ByteArrayInputStream(content));
        doAnswer(invocation -> null)
                .when(storage).put(any(), any(), eq((long) content.length), eq("video/mp4"));
        ImportLease lease = lease(importId, assetId, 1);
        doAnswer(invocation -> {
            throw new LeaseLostException(lease);
        }).when(persistence).checkpoint(any(), any(), any(), anyLong());
        assertThatThrownBy(() -> service(assets, storage, persistence, 1024)
                .copy(lease, new ImportLeaseControl()))
                .isInstanceOf(LeaseLostException.class);
        verify(storage).put(any(), any(), eq((long) content.length), eq("video/mp4"));
    }

    @Test
    void rejectsActualBytesAboveConfiguredLimit() throws Exception {
        UUID importId = UUID.randomUUID();
        MediaAsset asset = sourceAsset(importId, UUID.randomUUID(), 5);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        ProcessingCopyPersistence persistence = mock(ProcessingCopyPersistence.class);
        when(assets.findByImportIdAndAssetType(importId, MediaAssetType.SOURCE))
                .thenReturn(Optional.of(asset));
        when(storage.head("staging/source"))
                .thenReturn(Optional.of(metadata(5, "etag-1")));

        assertThatThrownBy(() -> service(assets, storage, persistence, 4)
                .copy(lease(importId, asset.getId(), 1), new ImportLeaseControl()))
                .extracting(exception -> ((ImportProcessingException) exception).failure().errorCode())
                .isEqualTo("SOURCE_SIZE_LIMIT_EXCEEDED");
    }

    private ProcessingCopyService service(
            MediaAssetRepository assets,
            ObjectStorage storage,
            ProcessingCopyPersistence persistence,
            long maxBytes) {
        return new ProcessingCopyService(
                assets, storage, persistence, tempDirectory.toString(), maxBytes);
    }

    private MediaAsset sourceAsset(UUID importId, UUID assetId, long sizeBytes) {
        return new MediaAsset(
                assetId,
                importId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                MediaAssetType.SOURCE,
                "staging/source",
                sizeBytes,
                "video/mp4",
                NOW.plusSeconds(3600),
                NOW);
    }

    private ImportLease lease(UUID importId, UUID assetId, long version) {
        return new ImportLease(
                importId,
                UUID.randomUUID(),
                "worker-1",
                version,
                1,
                ImportStage.RESOLVING,
                NOW.plusSeconds(60));
    }

    private ObjectStorage.ObjectMetadata metadata(long sizeBytes, String etag) {
        return new ObjectStorage.ObjectMetadata(sizeBytes, "video/mp4", etag);
    }

    private String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
