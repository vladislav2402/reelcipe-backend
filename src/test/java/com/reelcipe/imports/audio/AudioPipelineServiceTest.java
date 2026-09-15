package com.reelcipe.imports.audio;

import com.reelcipe.imports.AudioCheckpointPersistence;
import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.*;
import com.reelcipe.storage.InMemoryObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AudioPipelineServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    @TempDir
    Path workDirectory;

    @Test
    void normalizesAudioAndPersistsCheckpoint() {
        UUID importId = UUID.randomUUID();
        byte[] sourceBytes = "source-media".getBytes(StandardCharsets.UTF_8);
        MediaAsset source = source(importId, sourceBytes.length);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        AudioCheckpointPersistence checkpoints = mock(AudioCheckpointPersistence.class);
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        FakeAudioExtractor extractor = new FakeAudioExtractor();
        storage.putBytes(source.getProcessingKey(), sourceBytes, "video/mp4");
        when(assets.findByImportIdAndAssetType(importId, MediaAssetType.SOURCE))
                .thenReturn(Optional.of(source));

        AudioPipelineService service = service(assets, storage, extractor, checkpoints, 1000, 1000, 180);
        ImportLease lease = lease(importId);
        service.extract(lease, new ImportLeaseControl());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(checkpoints).checkpoint(
                eq(lease), any(UUID.class), key.capture(), any(String.class), anyLong(), eq(2));
        assertThat(key.getValue()).startsWith("processing/");
        assertThat(storage.head(key.getValue())).isPresent();
        assertThat(extractor.extracted).isTrue();
    }

    @Test
    void returnsDescriptionOnlyWithoutAudioStream() {
        UUID importId = UUID.randomUUID();
        byte[] sourceBytes = "video-without-audio".getBytes(StandardCharsets.UTF_8);
        MediaAsset source = source(importId, sourceBytes.length);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        AudioCheckpointPersistence checkpoints = mock(AudioCheckpointPersistence.class);
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        FakeAudioExtractor extractor = new FakeAudioExtractor();
        extractor.inputProbe = new AudioExtractor.Probe(
                Duration.ofSeconds(2), 0, 0, 0, null);
        storage.putBytes(source.getProcessingKey(), sourceBytes, "video/mp4");
        when(assets.findByImportIdAndAssetType(importId, MediaAssetType.SOURCE))
                .thenReturn(Optional.of(source));

        AudioExtractionResult result = service(
                assets, storage, extractor, checkpoints, 1000, 1000, 180)
                .extract(lease(importId), new ImportLeaseControl());

        assertThat(result.outcome()).isEqualTo(AudioExtractionResult.Outcome.DESCRIPTION_ONLY);
        assertThat(result.audioAssetId()).isNull();
        verify(checkpoints).descriptionOnly(any());
        verify(checkpoints, never()).checkpoint(any(), any(), any(), any(), anyLong(), any(Integer.class));
    }

    @Test
    void mapsExtractorTimeoutToTransientFailure() {
        UUID importId = UUID.randomUUID();
        byte[] sourceBytes = "source-media".getBytes(StandardCharsets.UTF_8);
        MediaAsset source = source(importId, sourceBytes.length);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        AudioCheckpointPersistence checkpoints = mock(AudioCheckpointPersistence.class);
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        FakeAudioExtractor extractor = new FakeAudioExtractor();
        extractor.failure = new AudioExtractionException(
                AudioExtractionException.Kind.TIMEOUT, "timeout");
        storage.putBytes(source.getProcessingKey(), sourceBytes, "video/mp4");
        when(assets.findByImportIdAndAssetType(importId, MediaAssetType.SOURCE))
                .thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service(
                assets, storage, extractor, checkpoints, 1000, 1000, 180)
                .extract(lease(importId), new ImportLeaseControl()))
                .extracting(exception -> ((ImportProcessingException) exception).failure().errorCode())
                .isEqualTo("AUDIO_TOOL_UNAVAILABLE");
    }

    private AudioPipelineService service(
            MediaAssetRepository assets,
            InMemoryObjectStorage storage,
            AudioExtractor extractor,
            AudioCheckpointPersistence checkpoints,
            long maxInputBytes,
            long maxOutputBytes,
            long maxDurationSeconds) {
        return new AudioPipelineService(
                assets,
                storage,
                extractor,
                checkpoints,
                workDirectory.toString(),
                maxInputBytes,
                maxOutputBytes,
                maxDurationSeconds,
                1);
    }

    private MediaAsset source(UUID importId, long sizeBytes) {
        MediaAsset source = new MediaAsset(
                UUID.randomUUID(),
                importId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                MediaAssetType.SOURCE,
                "staging/source",
                sizeBytes,
                "video/mp4",
                NOW.plusSeconds(3600),
                NOW);
        source.recordProcessingCopy(
                "processing/source", "source-hash", sizeBytes, java.time.Clock.systemUTC());
        return source;
    }

    private ImportLease lease(UUID importId) {
        return new ImportLease(
                importId,
                UUID.randomUUID(),
                "worker-1",
                1,
                1,
                ImportStage.EXTRACTING_AUDIO,
                NOW.plusSeconds(60));
    }

    private static final class FakeAudioExtractor implements AudioExtractor {
        private Probe inputProbe = new Probe(Duration.ofSeconds(2), 1, 48000, 2, "h264");
        private RuntimeException failure;
        private boolean extracted;

        @Override
        public Probe probe(Path input) {
            if (failure != null) {
                throw failure;
            }
            return input.getFileName().toString().endsWith(".flac")
                    ? new Probe(Duration.ofSeconds(2), 1, 16000, 1, "flac")
                    : inputProbe;
        }

        @Override
        public void extract(Path input, Path output) {
            try {
                Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                extracted = true;
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
