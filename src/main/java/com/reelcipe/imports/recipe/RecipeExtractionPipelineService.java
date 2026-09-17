package com.reelcipe.imports.recipe;

import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.recipe.domain.RecipeCandidate;
import com.reelcipe.imports.recipe.domain.RecipeCandidateRepository;
import com.reelcipe.imports.transcription.domain.Transcription;
import com.reelcipe.imports.transcription.domain.TranscriptionRepository;
import com.reelcipe.imports.transcription.domain.TranscriptionSegment;
import com.reelcipe.imports.transcription.domain.TranscriptionSegmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class RecipeExtractionPipelineService {
    private final ImportJobRepository jobs;
    private final TranscriptionRepository transcriptions;
    private final TranscriptionSegmentRepository segments;
    private final RecipeCandidateRepository candidates;
    private final RecipeExtractor extractor;
    private final RecipeCandidateValidator validator;
    private final RecipeExtractionCheckpointPersistence checkpoints;
    private final String targetLanguage;
    private final String promptVersion;
    private final String schemaVersion;
    private final String pipelineVersion;
    private final String provider;
    private final String model;
    private final int correctiveRetryLimit;

    public RecipeExtractionPipelineService(
            ImportJobRepository jobs,
            TranscriptionRepository transcriptions,
            TranscriptionSegmentRepository segments,
            RecipeCandidateRepository candidates,
            RecipeExtractor extractor,
            RecipeCandidateValidator validator,
            RecipeExtractionCheckpointPersistence checkpoints,
            @Value("${app.llm.target-language:en}") String targetLanguage,
            @Value("${app.llm.prompt-version:recipe-extraction-v1}") String promptVersion,
            @Value("${app.llm.schema-version:recipe-extraction-v1}") String schemaVersion,
            @Value("${app.llm.pipeline-version:b21-v1}") String pipelineVersion,
            @Value("${app.llm.provider:mock}") String provider,
            @Value("${app.llm.mock-model:reelcipe-mock-llm-v1}") String model,
            @Value("${app.llm.corrective-retry-limit:1}") int correctiveRetryLimit) {
        this.jobs = jobs;
        this.transcriptions = transcriptions;
        this.segments = segments;
        this.candidates = candidates;
        this.extractor = extractor;
        this.validator = validator;
        this.checkpoints = checkpoints;
        this.targetLanguage = targetLanguage;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.pipelineVersion = pipelineVersion;
        this.provider = provider;
        this.model = model;
        this.correctiveRetryLimit = Math.max(0, correctiveRetryLimit);
    }

    public void extract(ImportLease lease, ImportLeaseControl control) {
        if (!control.isValid()) {
            throw new LeaseLostException(lease);
        }
        ImportJob job = fencedJob(lease);
        RecipeTextSnapshot snapshot = snapshot(job);
        if (!snapshot.hasUsableText()) {
            throw new ImportProcessingException(ImportFailure.needsInput("NO_USABLE_TEXT"));
        }
        RecipeCandidate saved = candidates
                .findTopByImportIdAndInputHashOrderByCreatedAtDesc(
                        lease.importId(), snapshot.inputHash())
                .orElse(null);
        if (saved != null) {
            checkpoints.reuse(lease, saved);
            return;
        }

        RecipeExtractionCheckpointPersistence.StartedAttempt attempt = checkpoints.start(lease, snapshot);
        RecipeExtractor.Result result;
        RecipeCandidateValidator.Validation validation;
        try {
            result = extractor.extract(snapshot);
            validation = validator.validate(json(result), snapshot);
            int retries = 0;
            while (!validation.valid() && retries < correctiveRetryLimit) {
                ensureLease(lease, control, attempt.id());
                checkpoints.markUnknown(lease, attempt.id(), "LLM_INVALID_RESPONSE");
                attempt = checkpoints.start(lease, snapshot);
                result = extractor.correct(snapshot, json(result), validation.errors());
                validation = validator.validate(json(result), snapshot);
                retries++;
            }
            if (!validation.valid()) {
                checkpoints.markUnknown(lease, attempt.id(), "LLM_INVALID_RESPONSE");
                throw permanentError("LLM_INVALID_RESPONSE");
            }
            ensureLease(lease, control, attempt.id());
            checkpoints.checkpoint(lease, attempt, snapshot, result);
        } catch (RecipeExtractionException exception) {
            checkpoints.markUnknown(lease, attempt.id(), errorCode(exception));
            throw map(exception);
        } catch (LeaseLostException exception) {
            checkpoints.markStale(attempt.id());
            throw exception;
        } catch (ImportProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            checkpoints.markUnknown(lease, attempt.id(), "LLM_UNEXPECTED_ERROR");
            throw transientError("LLM_UNEXPECTED_ERROR", exception);
        }
    }

    private RecipeTextSnapshot snapshot(ImportJob job) {
        Transcription transcription = null;
        if (job.getTranscriptCheckpointRef() != null) {
            transcription = transcriptions.findById(UUID.fromString(job.getTranscriptCheckpointRef()))
                    .orElseThrow(() -> transientError("TRANSCRIPT_CHECKPOINT_NOT_FOUND"));
        }
        List<RecipeTextSnapshot.TranscriptSegment> transcriptSegments = transcription == null
                ? List.of()
                : segments.findByTranscriptionIdOrderBySegmentIndex(transcription.getId()).stream()
                .map(this::segment)
                .toList();
        String sourceLanguage = transcription == null ? targetLanguage : transcription.getLanguage();
        return new RecipeTextSnapshot(
                transcription == null ? null : transcription.getId(),
                transcription == null ? 0 : transcription.getVersion(),
                transcription == null ? null : transcription.getTranscriptHash(),
                transcriptSegments,
                job.getDescriptionText(),
                null,
                job.getAudioOutcome(),
                transcription == null ? null : transcription.getSpeechStatus(),
                sourceLanguage,
                targetLanguage,
                promptVersion,
                schemaVersion,
                pipelineVersion,
                provider,
                model);
    }

    private RecipeTextSnapshot.TranscriptSegment segment(TranscriptionSegment segment) {
        return new RecipeTextSnapshot.TranscriptSegment(
                segment.getId(),
                segment.getSegmentIndex(),
                segment.getStartMs(),
                segment.getEndMs(),
                segment.getText());
    }

    private ImportJob fencedJob(ImportLease lease) {
        return jobs.findFencedForUpdate(
                        lease.importId(), lease.owner(), lease.leaseVersion(), lease.inputRevision())
                .orElseThrow(() -> new LeaseLostException(lease));
    }

    private void ensureLease(ImportLease lease, ImportLeaseControl control, UUID attemptId) {
        if (!control.isValid()) {
            checkpoints.markStale(attemptId);
            throw new LeaseLostException(lease);
        }
    }

    private String json(RecipeExtractor.Result result) {
        if (result == null || result.candidateJson() == null || result.usage() == null) {
            return null;
        }
        return result.candidateJson();
    }

    private String errorCode(RecipeExtractionException exception) {
        if (Integer.valueOf(429).equals(exception.statusCode())) {
            return "LLM_RATE_LIMITED";
        }
        if (exception.statusCode() != null && exception.statusCode() >= 500) {
            return "LLM_PROVIDER_5XX";
        }
        return switch (exception.kind()) {
            case TIMEOUT -> "LLM_TIMEOUT";
            case UNKNOWN -> "LLM_UNKNOWN";
            case REFUSAL -> "LLM_REFUSAL";
            case INVALID_RESPONSE -> "LLM_INVALID_RESPONSE";
        };
    }

    private ImportProcessingException map(RecipeExtractionException exception) {
        if (exception.kind() == RecipeExtractionException.Kind.REFUSAL) {
            return new ImportProcessingException(
                    ImportFailure.permanent(errorCode(exception)),
                    exception);
        }
        return new ImportProcessingException(
                ImportFailure.transientError(errorCode(exception)),
                exception,
                exception.retryAfter());
    }

    private ImportProcessingException permanentError(String code) {
        return new ImportProcessingException(ImportFailure.permanent(code));
    }

    private ImportProcessingException transientError(String code) {
        return new ImportProcessingException(ImportFailure.transientError(code));
    }

    private ImportProcessingException transientError(String code, Throwable cause) {
        return new ImportProcessingException(ImportFailure.transientError(code), cause);
    }
}
