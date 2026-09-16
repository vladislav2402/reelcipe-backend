package com.reelcipe.imports.recipe;

import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.billing.QuotaService;
import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.recipe.domain.RecipeCandidate;
import com.reelcipe.imports.recipe.domain.RecipeCandidateRepository;
import com.reelcipe.imports.transcription.domain.Transcription;
import com.reelcipe.imports.transcription.domain.TranscriptionRepository;
import com.reelcipe.imports.transcription.domain.TranscriptionSegment;
import com.reelcipe.imports.transcription.domain.TranscriptionSegmentRepository;
import com.reelcipe.recipes.domain.*;
import com.reelcipe.sync.SyncChangeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RecipeFinalizationService {
    private final ImportJobRepository jobs;
    private final UserRepository users;
    private final RecipeCandidateRepository candidates;
    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final RecipeStepRepository steps;
    private final RecipeRevisionRepository revisions;
    private final RecipeImportResultRepository imports;
    private final RecipeEvidenceRepository evidence;
    private final TranscriptionRepository transcriptions;
    private final TranscriptionSegmentRepository segments;
    private final RecipeCandidateSemanticValidator validator;
    private final QuotaService quota;
    private final SyncChangeService sync;
    private final Clock clock;

    public RecipeFinalizationService(
            ImportJobRepository jobs,
            UserRepository users,
            RecipeCandidateRepository candidates,
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            RecipeStepRepository steps,
            RecipeRevisionRepository revisions,
            RecipeImportResultRepository imports,
            RecipeEvidenceRepository evidence,
            TranscriptionRepository transcriptions,
            TranscriptionSegmentRepository segments,
            RecipeCandidateSemanticValidator validator,
            QuotaService quota,
            SyncChangeService sync,
            Clock clock) {
        this.jobs = jobs;
        this.users = users;
        this.candidates = candidates;
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.steps = steps;
        this.revisions = revisions;
        this.imports = imports;
        this.evidence = evidence;
        this.transcriptions = transcriptions;
        this.segments = segments;
        this.validator = validator;
        this.quota = quota;
        this.sync = sync;
        this.clock = clock;
    }

    @Transactional
    public Recipe finalizeImport(ImportLease lease) {
        if (users.findLockedByIdAndStatus(lease.userId(), UserStatus.ACTIVE).isEmpty()) {
            throw new com.reelcipe.imports.domain.LeaseLostException(lease);
        }
        ImportJob job = fencedJob(lease);
        RecipeImportResult existing = imports.findByImportId(lease.importId()).orElse(null);
        if (existing != null) {
            job.completeStage(
                    lease.stage(),
                    existing.isReviewRequired()
                            ? ImportStatus.REVIEW_REQUIRED
                            : ImportStatus.READY,
                    clock);
            jobs.save(job);
            return recipes.findById(existing.getRecipeId()).orElseThrow();
        }
        RecipeCandidate candidate = candidate(job);
        RecipeTextSnapshot snapshot = snapshot(job, candidate);
        if (!snapshot.inputHash().equals(candidate.getInputHash())) {
            throw new ImportProcessingException(ImportFailure.permanent(
                    "PERMANENT_RECIPE_SNAPSHOT_MISMATCH"));
        }
        RecipeCandidateSemanticValidator.Result validation = validator.validate(
                candidate.getCandidateJson(), snapshot);
        if (validation.needsInput()) {
            throw new ImportProcessingException(ImportFailure.needsInput(
                    validation.errors().getFirst()));
        }
        if (!validation.valid()) {
            throw new ImportProcessingException(ImportFailure.permanent(
                    "PERMANENT_RECIPE_CANDIDATE_INVALID"));
        }

        Instant now = clock.instant();
        Recipe recipe = new Recipe(
                UUID.randomUUID(),
                lease.userId(),
                validation.document().title(),
                validation.document().language(),
                null,
                RecipeAnalysisMode.IMPORT,
                RecipeLibraryState.DRAFT,
                now);
        recipes.save(recipe);
        saveIngredients(recipe, validation.document().ingredients());
        saveSteps(recipe, validation.document().steps());
        revisions.save(new RecipeRevision(
                recipe.getId(),
                recipe.getVersion(),
                lease.userId(),
                candidate.getCandidateJson(),
                now));
        imports.save(new RecipeImportResult(
                UUID.randomUUID(),
                recipe.getId(),
                lease.importId(),
                lease.userId(),
                snapshot.sourceCoverage(),
                validation.reviewRequired(),
                now));
        saveEvidence(recipe, validation.document(), now);
        if (!quota.consumeImport(lease.userId(), lease.importId())) {
            throw new IllegalStateException("Import quota reservation is missing");
        }
        sync.record(lease.userId(), "recipe", recipe.getId(), "CREATE", recipe.getVersion());
        job.completeStage(
                lease.stage(),
                validation.reviewRequired()
                        ? ImportStatus.REVIEW_REQUIRED
                        : ImportStatus.READY,
                clock);
        jobs.save(job);
        return recipe;
    }

    private void saveIngredients(
            Recipe recipe,
            List<RecipeCandidateSemanticValidator.Ingredient> values) {
        for (int index = 0; index < values.size(); index++) {
            RecipeCandidateSemanticValidator.Ingredient value = values.get(index);
            ingredients.save(new RecipeIngredient(
                    UUID.randomUUID(),
                    recipe.getId(),
                    index,
                    value.name(),
                    value.amount(),
                    value.amountMax(),
                    value.unit(),
                    value.originalText()));
        }
    }

    private void saveSteps(
            Recipe recipe,
            List<RecipeCandidateSemanticValidator.Step> values) {
        for (int index = 0; index < values.size(); index++) {
            RecipeCandidateSemanticValidator.Step value = values.get(index);
            steps.save(new RecipeStep(
                    UUID.randomUUID(),
                    recipe.getId(),
                    index,
                    value.text()));
        }
    }

    private void saveEvidence(
            Recipe recipe,
            RecipeCandidateSemanticValidator.Document document,
            Instant now) {
        List<RecipeIngredient> savedIngredients = ingredients
                .findByRecipeIdOrderByPosition(recipe.getId());
        List<RecipeStep> savedSteps = steps.findByRecipeIdOrderByPosition(recipe.getId());
        for (int index = 0; index < document.ingredients().size(); index++) {
            UUID ingredientId = savedIngredients.get(index).getId();
            for (RecipeCandidateSemanticValidator.Evidence item
                    : document.ingredients().get(index).evidence()) {
                evidence.save(toEvidence(recipe, ingredientId, null, item, now));
            }
        }
        for (int index = 0; index < document.steps().size(); index++) {
            UUID stepId = savedSteps.get(index).getId();
            for (RecipeCandidateSemanticValidator.Evidence item
                    : document.steps().get(index).evidence()) {
                evidence.save(toEvidence(recipe, null, stepId, item, now));
            }
        }
    }

    private RecipeEvidence toEvidence(
            Recipe recipe,
            UUID ingredientId,
            UUID stepId,
            RecipeCandidateSemanticValidator.Evidence item,
            Instant now) {
        return new RecipeEvidence(
                UUID.randomUUID(),
                recipe.getId(),
                ingredientId,
                stepId,
                item.sourceType(),
                item.transcriptionId(),
                item.segmentId(),
                item.segmentIndex(),
                item.quote(),
                item.startMs(),
                item.endMs(),
                now);
    }

    private RecipeCandidate candidate(ImportJob job) {
        if (job.getRecipeCheckpointRef() == null) {
            throw new ImportProcessingException(ImportFailure.transientError(
                    "RECIPE_CANDIDATE_NOT_READY"));
        }
        return candidates.findById(UUID.fromString(job.getRecipeCheckpointRef()))
                .orElseThrow(() -> new ImportProcessingException(
                        ImportFailure.transientError("RECIPE_CANDIDATE_NOT_FOUND")));
    }

    private RecipeTextSnapshot snapshot(ImportJob job, RecipeCandidate candidate) {
        Transcription transcription = candidate.getTranscriptionId() == null
                ? null
                : transcriptions.findById(candidate.getTranscriptionId()).orElseThrow();
        List<RecipeTextSnapshot.TranscriptSegment> transcriptSegments = transcription == null
                ? List.of()
                : segments.findByTranscriptionIdOrderBySegmentIndex(transcription.getId()).stream()
                .map(this::segment)
                .toList();
        String language = candidate.getLanguage().toLowerCase();
        return new RecipeTextSnapshot(
                candidate.getTranscriptionId(),
                candidate.getTranscriptionVersion() == null ? 0 : candidate.getTranscriptionVersion(),
                transcription == null ? null : transcription.getTranscriptHash(),
                transcriptSegments,
                job.getDescriptionText(),
                null,
                job.getAudioOutcome(),
                transcription == null ? null : transcription.getSpeechStatus(),
                language,
                language,
                candidate.getPromptVersion(),
                candidate.getSchemaVersion(),
                candidate.getPipelineVersion(),
                candidate.getProvider(),
                candidate.getModel());
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
                        lease.importId(),
                        lease.owner(),
                        lease.leaseVersion(),
                        lease.inputRevision())
                .orElseThrow(() -> new com.reelcipe.imports.domain.LeaseLostException(lease));
    }
}
