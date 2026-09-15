package com.reelcipe.imports.transcription;

import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.transcription.domain.Transcription;
import com.reelcipe.imports.transcription.domain.TranscriptionRepository;
import com.reelcipe.storage.ObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class TranscriptionPipelineService {
    private final MediaAssetRepository assets;
    private final TranscriptionRepository transcriptions;
    private final ObjectStorage storage;
    private final SpeechTranscriber transcriber;
    private final TranscriptionCheckpointPersistence checkpoints;

    public TranscriptionPipelineService(
            MediaAssetRepository assets,
            TranscriptionRepository transcriptions,
            ObjectStorage storage,
            SpeechTranscriber transcriber,
            TranscriptionCheckpointPersistence checkpoints) {
        this.assets = assets;
        this.transcriptions = transcriptions;
        this.storage = storage;
        this.transcriber = transcriber;
        this.checkpoints = checkpoints;
    }

    public void transcribe(ImportLease lease, ImportLeaseControl control) {
        if (!control.isValid()) {
            throw new LeaseLostException(lease);
        }
        MediaAsset audio = assets.findByImportIdAndAssetType(
                        lease.importId(), MediaAssetType.NORMALIZED_AUDIO)
                .orElseThrow(() -> transientError("NORMALIZED_AUDIO_NOT_READY"));
        if (audio.getProcessingKey() == null || audio.getSha256() == null) {
            throw transientError("NORMALIZED_AUDIO_NOT_READY");
        }
        Transcription saved = transcriptions
                .findTopByImportIdAndAudioAssetIdOrderByVersionDesc(
                        lease.importId(), audio.getId())
                .orElse(null);
        if (saved != null && saved.getInputHash().equals(audio.getSha256())) {
            checkpoints.reuse(lease, saved, audio.getProcessingKey());
            return;
        }

        TranscriptionCheckpointPersistence.StartedAttempt attempt = checkpoints.start(
                lease,
                audio.getSha256());
        try (InputStream input = storage.open(audio.getProcessingKey())) {
            if (!control.isValid()) {
                checkpoints.markStale(attempt.id());
                throw new LeaseLostException(lease);
            }
            SpeechTranscriber.Result result = transcriber.transcribe(
                    input,
                    new SpeechTranscriber.Request(
                            audio.getSha256(),
                            audio.getDurationSeconds() == null ? 0 : audio.getDurationSeconds()));
            if (!control.isValid()) {
                checkpoints.markStale(attempt.id());
                throw new LeaseLostException(lease);
            }
            checkpoints.checkpoint(
                    lease,
                    attempt,
                    audio.getId(),
                    audio.getProcessingKey(),
                    audio.getSha256(),
                    audio.getDurationSeconds() == null ? 0 : audio.getDurationSeconds(),
                    result);
        } catch (SpeechTranscriptionException exception) {
            checkpoints.markUnknown(lease, attempt.id(), errorCode(exception));
            throw map(exception);
        } catch (LeaseLostException exception) {
            checkpoints.markStale(attempt.id());
            throw exception;
        } catch (RuntimeException exception) {
            checkpoints.markUnknown(lease, attempt.id(), "ASR_UNEXPECTED_ERROR");
            throw transientError("ASR_UNEXPECTED_ERROR", exception);
        } catch (java.io.IOException exception) {
            checkpoints.markUnknown(lease, attempt.id(), "ASR_AUDIO_READ_FAILED");
            throw transientError("ASR_AUDIO_READ_FAILED", exception);
        }
    }

    private String errorCode(SpeechTranscriptionException exception) {
        return switch (exception.kind()) {
            case TIMEOUT -> "ASR_TIMEOUT";
            case UNKNOWN -> "ASR_UNKNOWN";
            case INVALID_RESPONSE -> "ASR_INVALID_RESPONSE";
        };
    }

    private ImportProcessingException map(SpeechTranscriptionException exception) {
        if (exception.kind() == SpeechTranscriptionException.Kind.INVALID_RESPONSE) {
            return new ImportProcessingException(
                    ImportFailure.permanent("ASR_INVALID_RESPONSE"), exception);
        }
        return transientError(errorCode(exception), exception);
    }

    private ImportProcessingException transientError(String code) {
        return new ImportProcessingException(ImportFailure.transientError(code));
    }

    private ImportProcessingException transientError(String code, Throwable cause) {
        return new ImportProcessingException(ImportFailure.transientError(code), cause);
    }
}
