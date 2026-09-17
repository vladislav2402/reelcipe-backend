package com.reelcipe.imports.transcription;

import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.transcription.domain.*;
import com.reelcipe.operations.deletion.DeletionTaskService;
import com.reelcipe.providers.ProviderAdmissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TranscriptionCheckpointPersistence {
    private final ImportJobRepository jobs;
    private final AiAttemptRepository attempts;
    private final TranscriptionRepository transcriptions;
    private final TranscriptionSegmentRepository segments;
    private final DeletionTaskService deletions;
    private final Clock clock;
    private final String provider;
    private final String model;
    private final ProviderAdmissionService admissions;

    public TranscriptionCheckpointPersistence(
            ImportJobRepository jobs,
            AiAttemptRepository attempts,
            TranscriptionRepository transcriptions,
            TranscriptionSegmentRepository segments,
            DeletionTaskService deletions,
            Clock clock,
            ProviderAdmissionService admissions,
            @Value("${app.asr.provider:mock}") String provider,
            @Value("${app.asr.mock-model:reelcipe-mock-asr-v1}") String model) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.transcriptions = transcriptions;
        this.segments = segments;
        this.deletions = deletions;
        this.clock = clock;
        this.admissions = admissions;
        this.provider = provider;
        this.model = model;
    }

    @Transactional
    public StartedAttempt start(ImportLease lease, String inputHash) {
        fencedJob(lease);
        int nextAttempt = attempts.findTopByImportIdAndKindOrderByAttemptNumberDesc(
                        lease.importId(), AiAttemptKind.ASR)
                .map(attempt -> attempt.getAttemptNumber() + 1)
                .orElse(1);
        UUID attemptId = UUID.randomUUID();
        ProviderAdmissionService.Reservation reservation = admissions.reserve(
                new ProviderAdmissionService.Request(
                        attemptId,
                        lease.importId(),
                        lease.userId(),
                        provider,
                        "ASR",
                        estimateUnits(lease, inputHash)));
        AiAttempt attempt = new AiAttempt(
                attemptId,
                lease.importId(),
                lease.userId(),
                AiAttemptKind.ASR,
                nextAttempt,
                inputHash,
                provider,
                model,
                reservation.id(),
                clock.instant());
        attempts.save(attempt);
        return new StartedAttempt(attempt.getId(), provider, model, reservation.id());
    }

    @Transactional
    public Transcription checkpoint(
            ImportLease lease,
            StartedAttempt started,
            UUID audioAssetId,
            String audioProcessingKey,
            String inputHash,
            int durationSeconds,
            SpeechTranscriber.Result result) {
        ImportJob job = fencedJob(lease);
        Transcription transcription = transcriptions
                .findTopByImportIdAndAudioAssetIdOrderByVersionDesc(
                        lease.importId(), audioAssetId)
                .filter(saved -> saved.getInputHash().equals(inputHash))
                .orElseGet(() -> createTranscription(
                        lease,
                        audioAssetId,
                        inputHash,
                        durationSeconds,
                        result));
        AiAttempt attempt = attempts.findById(started.id())
                .orElseThrow(() -> new IllegalStateException("ASR attempt was not found"));
        attempt.succeed(result.usage().inputSeconds(), result.usage().units(), clock);
        attempts.save(attempt);
        com.reelcipe.providers.ProviderAdmissionService.ConsumeResult settlement = admissions.consume(
                started.admissionId(),
                result.usage().units() + result.usage().inputSeconds());
        if (!settlement.accepted()) {
            attempt.markUnknown("PROVIDER_USAGE_OVER_BUDGET", clock);
            attempts.save(attempt);
            throw new com.reelcipe.providers.ProviderAdmissionException(
                    "PROVIDER_USAGE_OVER_BUDGET",
                    settlement.retryAfter());
        }
        job.checkpoint(lease.stage(), transcription.getId().toString(), clock);
        job.completeStage(lease.stage(), ImportStatus.EXTRACTING_RECIPE, clock);
        jobs.save(job);
        deletions.enqueue(
                lease.userId(),
                "NORMALIZED_AUDIO",
                audioProcessingKey,
                "normalized-audio:" + lease.importId() + ":" + audioAssetId);
        return transcription;
    }

    @Transactional
    public void reuse(ImportLease lease, Transcription transcription, String audioProcessingKey) {
        ImportJob job = fencedJob(lease);
        job.checkpoint(lease.stage(), transcription.getId().toString(), clock);
        job.completeStage(lease.stage(), ImportStatus.EXTRACTING_RECIPE, clock);
        jobs.save(job);
        deletions.enqueue(
                lease.userId(),
                "NORMALIZED_AUDIO",
                audioProcessingKey,
                "normalized-audio:" + lease.importId() + ":" + transcription.getAudioAssetId());
    }

    @Transactional
    public void markUnknown(ImportLease lease, UUID attemptId, String errorCode) {
        if (jobs.findFencedForUpdate(
                        lease.importId(),
                        lease.owner(),
                        lease.leaseVersion(),
                        lease.inputRevision())
                .isEmpty()) {
            return;
        }
        attempts.findById(attemptId).ifPresent(attempt -> {
            attempt.markUnknown(errorCode, clock);
            attempts.save(attempt);
            admissions.unknown(attempt.getProviderAdmissionId(), errorCode);
        });
    }

    @Transactional
    public void markStale(UUID attemptId) {
        attempts.findById(attemptId).ifPresent(attempt -> {
            attempt.markStale(clock);
            attempts.save(attempt);
            admissions.stale(attempt.getProviderAdmissionId());
        });
    }

    private Transcription createTranscription(
            ImportLease lease,
            UUID audioAssetId,
            String inputHash,
            int durationSeconds,
            SpeechTranscriber.Result result) {
        validateResult(result, durationSeconds);
        int version = transcriptions.findTopByImportIdOrderByVersionDesc(lease.importId())
                .map(transcription -> transcription.getVersion() + 1)
                .orElse(1);
        String fullText = result.segments().stream()
                .map(SpeechTranscriber.Segment::text)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        String transcriptHash = hash(result, fullText);
        Transcription transcription = new Transcription(
                UUID.randomUUID(),
                lease.importId(),
                lease.userId(),
                audioAssetId,
                version,
                inputHash,
                result.language(),
                result.speechStatus(),
                transcriptHash,
                fullText,
                durationSeconds,
                provider,
                model,
                clock.instant());
        transcriptions.save(transcription);
        for (int index = 0; index < result.segments().size(); index++) {
            SpeechTranscriber.Segment segment = result.segments().get(index);
            segments.save(new TranscriptionSegment(
                    UUID.randomUUID(),
                    transcription.getId(),
                    index,
                    segment.startMs(),
                    segment.endMs(),
                    segment.text()));
        }
        return transcription;
    }

    private void validateResult(SpeechTranscriber.Result result, int durationSeconds) {
        if (result == null || result.language() == null || result.language().isBlank()
                || result.speechStatus() == null || result.usage() == null) {
            throw new SpeechTranscriptionException(
                    SpeechTranscriptionException.Kind.INVALID_RESPONSE,
                    "ASR response is incomplete");
        }
        long durationMs = durationSeconds * 1000L;
        for (SpeechTranscriber.Segment segment : result.segments()) {
            if (segment.text() == null || segment.text().isBlank()
                    || segment.startMs() < 0
                    || segment.endMs() <= segment.startMs()
                    || segment.endMs() > durationMs) {
                throw new SpeechTranscriptionException(
                        SpeechTranscriptionException.Kind.INVALID_RESPONSE,
                        "ASR segment is outside audio duration");
            }
        }
        if (result.speechStatus().name().equals("SPEECH")
                && result.segments().isEmpty()) {
            throw new SpeechTranscriptionException(
                    SpeechTranscriptionException.Kind.INVALID_RESPONSE,
                    "ASR speech result has no segments");
        }
    }

    private String hash(SpeechTranscriber.Result result, String fullText) {
        String value = result.language() + "|" + result.speechStatus()
                + "|" + fullText + "|" + result.segments();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ImportJob fencedJob(ImportLease lease) {
        return jobs.findFencedForUpdate(
                        lease.importId(),
                        lease.owner(),
                        lease.leaseVersion(),
                        lease.inputRevision())
                .orElseThrow(() -> new LeaseLostException(lease));
    }

    private long estimateUnits(ImportLease lease, String inputHash) {
        return Math.max(1, inputHash == null ? 1 : inputHash.length());
    }

    public record StartedAttempt(UUID id, String provider, String model, UUID admissionId) {
        public StartedAttempt(UUID id, String provider, String model) {
            this(id, provider, model, null);
        }
    }
}
