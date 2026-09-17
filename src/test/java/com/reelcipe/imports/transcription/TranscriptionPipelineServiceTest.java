package com.reelcipe.imports.transcription;

import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.transcription.domain.SpeechStatus;
import com.reelcipe.imports.transcription.domain.TranscriptionRepository;
import com.reelcipe.storage.InMemoryObjectStorage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TranscriptionPipelineServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    @Test
    void sendsAudioOutsidePersistenceAndCheckpointsResult() {
        UUID importId = UuidV7.randomUuid();
        MediaAsset audio = audio(importId);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        TranscriptionRepository transcriptions = mock(TranscriptionRepository.class);
        TranscriptionCheckpointPersistence checkpoints = mock(TranscriptionCheckpointPersistence.class);
        SpeechTranscriber transcriber = mock(SpeechTranscriber.class);
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        storage.putBytes(audio.getProcessingKey(), new byte[] {1, 2, 3}, "audio/flac");
        when(assets.findByImportIdAndAssetType(importId, MediaAssetType.NORMALIZED_AUDIO))
                .thenReturn(Optional.of(audio));
        when(transcriptions.findTopByImportIdAndAudioAssetIdOrderByVersionDesc(
                importId, audio.getId()))
                .thenReturn(Optional.empty());
        TranscriptionCheckpointPersistence.StartedAttempt started =
                new TranscriptionCheckpointPersistence.StartedAttempt(
                        UuidV7.randomUuid(), "mock", "mock-v1");
        when(checkpoints.start(any(), eq("audio-hash"))).thenReturn(started);
        when(transcriber.transcribe(any(), any())).thenReturn(new SpeechTranscriber.Result(
                "en",
                SpeechStatus.SPEECH,
                List.of(new SpeechTranscriber.Segment(0, 1000, "Boil pasta.")),
                new SpeechTranscriber.Usage(4, 1)));

        TranscriptionPipelineService service = new TranscriptionPipelineService(
                assets,
                transcriptions,
                storage,
                transcriber,
                checkpoints);
        service.transcribe(lease(importId), new ImportLeaseControl());

        verify(transcriber).transcribe(any(), any());
        verify(checkpoints).checkpoint(
                any(), eq(started), eq(audio.getId()), eq(audio.getProcessingKey()),
                eq("audio-hash"), eq(4), any());
    }

    @Test
    void marksUnknownAttemptAndReturnsRetryableFailure() {
        UUID importId = UuidV7.randomUuid();
        MediaAsset audio = audio(importId);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        TranscriptionRepository transcriptions = mock(TranscriptionRepository.class);
        TranscriptionCheckpointPersistence checkpoints = mock(TranscriptionCheckpointPersistence.class);
        SpeechTranscriber transcriber = mock(SpeechTranscriber.class);
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        storage.putBytes(audio.getProcessingKey(), new byte[] {1}, "audio/flac");
        when(assets.findByImportIdAndAssetType(importId, MediaAssetType.NORMALIZED_AUDIO))
                .thenReturn(Optional.of(audio));
        when(transcriptions.findTopByImportIdAndAudioAssetIdOrderByVersionDesc(
                importId, audio.getId()))
                .thenReturn(Optional.empty());
        UUID attemptId = UuidV7.randomUuid();
        when(checkpoints.start(any(), eq("audio-hash"))).thenReturn(
                new TranscriptionCheckpointPersistence.StartedAttempt(
                        attemptId, "mock", "mock-v1"));
        when(transcriber.transcribe(any(), any())).thenThrow(new SpeechTranscriptionException(
                SpeechTranscriptionException.Kind.TIMEOUT,
                "timeout"));

        TranscriptionPipelineService service = new TranscriptionPipelineService(
                assets,
                transcriptions,
                storage,
                transcriber,
                checkpoints);

        assertThatThrownBy(() -> service.transcribe(lease(importId), new ImportLeaseControl()))
                .isInstanceOf(ImportProcessingException.class)
                .extracting(exception -> ((ImportProcessingException) exception).failure().errorCode())
                .isEqualTo("ASR_TIMEOUT");
        verify(checkpoints).markUnknown(any(), eq(attemptId), eq("ASR_TIMEOUT"));
        verify(checkpoints, never()).checkpoint(
                any(), any(), any(), any(), any(), anyInt(), any());
    }

    private MediaAsset audio(UUID importId) {
        return MediaAsset.normalizedAudio(
                UuidV7.randomUuid(),
                importId,
                UuidV7.randomUuid(),
                "processing/audio.flac",
                "audio-hash",
                3,
                4,
                NOW.plusSeconds(3600),
                NOW);
    }

    private ImportLease lease(UUID importId) {
        return new ImportLease(
                importId,
                UuidV7.randomUuid(),
                "worker-1",
                1,
                1,
                ImportStage.TRANSCRIBING,
                NOW.plusSeconds(60));
    }
}
