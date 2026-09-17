package com.reelcipe.imports.recipe;

import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.recipe.domain.RecipeCandidate;
import com.reelcipe.imports.recipe.domain.RecipeCandidateRepository;
import com.reelcipe.imports.transcription.domain.AiAttempt;
import com.reelcipe.imports.transcription.domain.AiAttemptKind;
import com.reelcipe.imports.transcription.domain.AiAttemptRepository;
import com.reelcipe.providers.ProviderAdmissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class RecipeExtractionCheckpointPersistence {
    private final ImportJobRepository jobs;
    private final AiAttemptRepository attempts;
    private final RecipeCandidateRepository candidates;
    private final Clock clock;
    private final String provider;
    private final String model;
    private final ProviderAdmissionService admissions;

    public RecipeExtractionCheckpointPersistence(
            ImportJobRepository jobs,
            AiAttemptRepository attempts,
            RecipeCandidateRepository candidates,
            Clock clock,
            ProviderAdmissionService admissions,
            @Value("${app.llm.provider:mock}") String provider,
            @Value("${app.llm.mock-model:reelcipe-mock-llm-v1}") String model) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.candidates = candidates;
        this.clock = clock;
        this.admissions = admissions;
        this.provider = provider;
        this.model = model;
    }

    @Transactional
    public StartedAttempt start(ImportLease lease, RecipeTextSnapshot snapshot) {
        fencedJob(lease);
        int nextAttempt = attempts.findTopByImportIdAndKindOrderByAttemptNumberDesc(
                        lease.importId(), AiAttemptKind.LLM)
                .map(attempt -> attempt.getAttemptNumber() + 1)
                .orElse(1);
        UUID attemptId = UUID.randomUUID();
        ProviderAdmissionService.Reservation reservation = admissions.reserve(
                new ProviderAdmissionService.Request(
                        attemptId,
                        lease.importId(),
                        lease.userId(),
                        provider,
                        "LLM",
                        estimateUnits(snapshot)));
        AiAttempt attempt = new AiAttempt(
                attemptId,
                lease.importId(),
                lease.userId(),
                AiAttemptKind.LLM,
                nextAttempt,
                snapshot.inputHash(),
                provider,
                model,
                reservation.id(),
                clock.instant());
        attempts.save(attempt);
        return new StartedAttempt(attempt.getId(), provider, model, reservation.id());
    }

    @Transactional
    public RecipeCandidate checkpoint(
            ImportLease lease,
            StartedAttempt started,
            RecipeTextSnapshot snapshot,
            RecipeExtractor.Result result) {
        ImportJob job = fencedJob(lease);
        RecipeCandidate candidate = candidates
                .findTopByImportIdAndInputHashOrderByCreatedAtDesc(
                        lease.importId(), snapshot.inputHash())
                .orElseGet(() -> createCandidate(lease, snapshot, result));
        AiAttempt attempt = attempts.findById(started.id())
                .orElseThrow(() -> new IllegalStateException("LLM attempt was not found"));
        attempt.succeed(result.usage().inputUnits(), result.usage().outputUnits(), clock);
        attempts.save(attempt);
        com.reelcipe.providers.ProviderAdmissionService.ConsumeResult settlement = admissions.consume(
                started.admissionId(),
                result.usage().inputUnits() + result.usage().outputUnits());
        if (!settlement.accepted()) {
            attempt.markUnknown("PROVIDER_USAGE_OVER_BUDGET", clock);
            attempts.save(attempt);
            throw new com.reelcipe.providers.ProviderAdmissionException(
                    "PROVIDER_USAGE_OVER_BUDGET",
                    settlement.retryAfter());
        }
        job.checkpoint(lease.stage(), candidate.getId().toString(), clock);
        job.completeStage(lease.stage(), ImportStatus.VALIDATING, clock);
        jobs.save(job);
        return candidate;
    }

    @Transactional
    public void reuse(ImportLease lease, RecipeCandidate candidate) {
        ImportJob job = fencedJob(lease);
        job.checkpoint(lease.stage(), candidate.getId().toString(), clock);
        job.completeStage(lease.stage(), ImportStatus.VALIDATING, clock);
        jobs.save(job);
    }

    @Transactional
    public void markUnknown(ImportLease lease, UUID attemptId, String errorCode) {
        if (jobs.findFencedForUpdate(
                lease.importId(), lease.owner(), lease.leaseVersion(), lease.inputRevision()).isEmpty()) {
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

    private RecipeCandidate createCandidate(
            ImportLease lease,
            RecipeTextSnapshot snapshot,
            RecipeExtractor.Result result) {
        RecipeCandidate candidate = new RecipeCandidate(
                UUID.randomUUID(),
                lease.importId(),
                lease.userId(),
                snapshot.inputHash(),
                snapshot.transcriptionId(),
                snapshot.transcriptionId() == null ? null : snapshot.transcriptionVersion(),
                snapshot.schemaVersion(),
                snapshot.promptVersion(),
                snapshot.pipelineVersion(),
                snapshot.targetLanguage(),
                snapshot.sourceCoverage(),
                result.candidateJson(),
                resultProvider(snapshot),
                resultModel(snapshot),
                clock.instant());
        return candidates.save(candidate);
    }

    private String resultProvider(RecipeTextSnapshot snapshot) {
        return snapshot.provider() == null ? provider : snapshot.provider();
    }

    private String resultModel(RecipeTextSnapshot snapshot) {
        return snapshot.model() == null ? model : snapshot.model();
    }

    private ImportJob fencedJob(ImportLease lease) {
        return jobs.findFencedForUpdate(
                        lease.importId(),
                        lease.owner(),
                        lease.leaseVersion(),
                        lease.inputRevision())
                .orElseThrow(() -> new LeaseLostException(lease));
    }

    private long estimateUnits(RecipeTextSnapshot snapshot) {
        long textLength = snapshot.transcriptSegments().stream()
                .mapToLong(segment -> segment.text() == null ? 0 : segment.text().length())
                .sum();
        textLength += snapshot.authorDescription() == null
                ? 0
                : snapshot.authorDescription().length();
        textLength += snapshot.userText() == null ? 0 : snapshot.userText().length();
        return Math.max(1000, textLength * 2L);
    }

    public record StartedAttempt(UUID id, String provider, String model, UUID admissionId) {
        public StartedAttempt(UUID id, String provider, String model) {
            this(id, provider, model, null);
        }
    }
}
