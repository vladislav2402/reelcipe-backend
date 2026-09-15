package com.reelcipe.imports.domain;

import jakarta.persistence.*;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "import_jobs")
public class ImportJob {
    private static final Map<ImportStatus, Set<ImportStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(ImportStatus.AWAITING_UPLOAD, EnumSet.of(ImportStatus.QUEUED, ImportStatus.CANCELLED, ImportStatus.EXPIRED)),
            Map.entry(ImportStatus.QUEUED, EnumSet.of(ImportStatus.RESOLVING, ImportStatus.CANCELLED, ImportStatus.EXPIRED)),
            Map.entry(ImportStatus.RESOLVING, EnumSet.of(ImportStatus.EXTRACTING_AUDIO, ImportStatus.RETRY_WAIT,
                    ImportStatus.NEEDS_INPUT, ImportStatus.FAILED, ImportStatus.CANCELLED, ImportStatus.EXPIRED)),
            Map.entry(ImportStatus.EXTRACTING_AUDIO, EnumSet.of(ImportStatus.TRANSCRIBING,
                    ImportStatus.EXTRACTING_RECIPE, ImportStatus.RETRY_WAIT,
                    ImportStatus.FAILED, ImportStatus.CANCELLED, ImportStatus.EXPIRED)),
            Map.entry(ImportStatus.TRANSCRIBING, EnumSet.of(ImportStatus.EXTRACTING_RECIPE, ImportStatus.RETRY_WAIT,
                    ImportStatus.FAILED, ImportStatus.CANCELLED, ImportStatus.EXPIRED)),
            Map.entry(ImportStatus.EXTRACTING_RECIPE, EnumSet.of(ImportStatus.VALIDATING, ImportStatus.RETRY_WAIT,
                    ImportStatus.NEEDS_INPUT, ImportStatus.FAILED, ImportStatus.CANCELLED, ImportStatus.EXPIRED)),
            Map.entry(ImportStatus.VALIDATING, EnumSet.of(ImportStatus.READY, ImportStatus.REVIEW_REQUIRED,
                    ImportStatus.RETRY_WAIT, ImportStatus.NEEDS_INPUT, ImportStatus.FAILED,
                    ImportStatus.CANCELLED, ImportStatus.EXPIRED)),
            Map.entry(ImportStatus.RETRY_WAIT, EnumSet.of(ImportStatus.FAILED, ImportStatus.CANCELLED, ImportStatus.EXPIRED)),
            Map.entry(ImportStatus.NEEDS_INPUT, EnumSet.of(ImportStatus.QUEUED, ImportStatus.CANCELLED, ImportStatus.EXPIRED)),
            Map.entry(ImportStatus.READY, EnumSet.noneOf(ImportStatus.class)),
            Map.entry(ImportStatus.REVIEW_REQUIRED, EnumSet.noneOf(ImportStatus.class)),
            Map.entry(ImportStatus.FAILED, EnumSet.noneOf(ImportStatus.class)),
            Map.entry(ImportStatus.CANCELLED, EnumSet.noneOf(ImportStatus.class)),
            Map.entry(ImportStatus.EXPIRED, EnumSet.noneOf(ImportStatus.class)));

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "client_request_id", nullable = false)
    private UUID clientRequestId;
    @Column(name = "input_hash")
    private String inputHash;
    @Column(name = "input_revision", nullable = false)
    private long inputRevision;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private ImportSourceType sourceType;
    @Column(name = "source_url")
    private String sourceUrl;
    @Enumerated(EnumType.STRING)
    @Column(name = "media_kind")
    private ImportMediaKind mediaKind;
    @Column(name = "file_name")
    private String fileName;
    @Column(name = "content_type")
    private String contentType;
    @Column(name = "expected_size_bytes")
    private Long expectedSizeBytes;
    @Column(name = "description_text")
    private String descriptionText;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "resume_stage", nullable = false)
    private ImportStage resumeStage;
    @Column(nullable = false)
    private int attempts;
    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_stage", nullable = false)
    private ImportStage attemptStage;
    @Column(name = "stage_attempts", nullable = false)
    private int stageAttempts;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    @Column(name = "lease_owner")
    private String leaseOwner;
    @Column(name = "lease_version", nullable = false)
    private long leaseVersion;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "processing_deadline_at", nullable = false)
    private Instant processingDeadlineAt;
    @Column(name = "input_deadline_at")
    private Instant inputDeadlineAt;
    @Column(name = "error_code")
    private String errorCode;
    @Column(name = "source_checkpoint_ref")
    private String sourceCheckpointRef;
    @Column(name = "audio_checkpoint_ref")
    private String audioCheckpointRef;
    @Enumerated(EnumType.STRING)
    @Column(name = "audio_outcome")
    private AudioOutcome audioOutcome;
    @Column(name = "transcript_checkpoint_ref")
    private String transcriptCheckpointRef;
    @Column(name = "recipe_checkpoint_ref")
    private String recipeCheckpointRef;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected ImportJob() {
    }

    public ImportJob(
            UUID id,
            UUID userId,
            UUID clientRequestId,
            ImportSourceType sourceType,
            String sourceUrl,
            String inputHash,
            ImportStatus status,
            ImportStage resumeStage,
            Instant processingDeadlineAt,
            Instant inputDeadlineAt,
            Instant now) {
        this(id, userId, clientRequestId, sourceType, sourceUrl, null, null, null, null, null,
                inputHash, status, resumeStage, processingDeadlineAt, inputDeadlineAt, now);
    }

    public ImportJob(
            UUID id,
            UUID userId,
            UUID clientRequestId,
            ImportSourceType sourceType,
            String sourceUrl,
            ImportMediaKind mediaKind,
            String fileName,
            String contentType,
            Long expectedSizeBytes,
            String descriptionText,
            String inputHash,
            ImportStatus status,
            ImportStage resumeStage,
            Instant processingDeadlineAt,
            Instant inputDeadlineAt,
            Instant now) {
        this.id = id;
        this.userId = userId;
        this.clientRequestId = clientRequestId;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.mediaKind = mediaKind;
        this.fileName = fileName;
        this.contentType = contentType;
        this.expectedSizeBytes = expectedSizeBytes;
        this.descriptionText = descriptionText;
        this.inputHash = inputHash;
        this.inputRevision = 1;
        this.status = status;
        this.resumeStage = resumeStage;
        this.attemptStage = resumeStage;
        this.stageAttempts = 0;
        this.processingDeadlineAt = processingDeadlineAt;
        this.inputDeadlineAt = inputDeadlineAt;
        this.nextAttemptAt = status == ImportStatus.QUEUED ? now : null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void transitionTo(ImportStatus target, Clock clock) {
        if (!TRANSITIONS.get(status).contains(target)) {
            throw new IllegalStateException("Forbidden import transition: " + status + " -> " + target);
        }
        status = target;
        updatedAt = clock.instant();
        if (target == ImportStatus.RESOLVING || target == ImportStatus.EXTRACTING_AUDIO
                || target == ImportStatus.TRANSCRIBING || target == ImportStatus.EXTRACTING_RECIPE
                || target == ImportStatus.VALIDATING) {
            attemptStage = ImportStage.valueOf(target.name());
            stageAttempts = 0;
        }
        if (isTerminal(target)) {
            completedAt = updatedAt;
            leaseOwner = null;
            leaseUntil = null;
        }
        if (target == ImportStatus.QUEUED) {
            nextAttemptAt = updatedAt;
        }
    }

    public void claimForProcessing(String owner, Instant leaseUntil, Clock clock) {
        if (owner == null || owner.isBlank() || leaseUntil == null) {
            throw new IllegalArgumentException("Import lease data is required");
        }
        if (!isClaimableStatus(status)) {
            throw new IllegalStateException("Import is not claimable: " + status);
        }
        if (status == ImportStatus.QUEUED || status == ImportStatus.RETRY_WAIT) {
            status = ImportStatus.valueOf(resumeStage.name());
        }
        ImportStage claimedStage = ImportStage.valueOf(status.name());
        if (attemptStage != claimedStage) {
            attemptStage = claimedStage;
            stageAttempts = 0;
        }
        leaseOwner = owner;
        leaseVersion++;
        this.leaseUntil = leaseUntil;
        nextAttemptAt = null;
        updatedAt = clock.instant();
    }

    public void scheduleRetry(Instant nextAttempt, String error, Clock clock) {
        if (!isActiveStage(status)) {
            throw new IllegalStateException("Only active stages can be retried");
        }
        resumeStage = ImportStage.valueOf(status.name());
        status = ImportStatus.RETRY_WAIT;
        nextAttemptAt = nextAttempt;
        errorCode = error;
        attempts++;
        attemptStage = resumeStage;
        stageAttempts++;
        leaseOwner = null;
        leaseUntil = null;
        updatedAt = clock.instant();
    }

    public void resumeRetry(Clock clock) {
        if (status != ImportStatus.RETRY_WAIT) {
            throw new IllegalStateException("Only retrying imports can resume");
        }
        status = ImportStatus.valueOf(resumeStage.name());
        nextAttemptAt = clock.instant();
        updatedAt = nextAttemptAt;
    }

    public void retry(Clock clock) {
        if (status != ImportStatus.FAILED) {
            throw new IllegalStateException("Only failed imports can retry");
        }
        status = ImportStatus.QUEUED;
        nextAttemptAt = clock.instant();
        errorCode = null;
        attempts = 0;
        stageAttempts = 0;
        completedAt = null;
        leaseOwner = null;
        leaseUntil = null;
        updatedAt = nextAttemptAt;
    }

    public void changeInputRevision(String newInputHash, boolean newFile, Clock clock) {
        changeInputRevision(newInputHash, descriptionText, newFile, null, Integer.MAX_VALUE, clock);
    }

    public void changeInputRevision(
            String newInputHash,
            String newDescriptionText,
            boolean newFile,
            Instant newInputDeadline,
            int maxContinuations,
            Clock clock) {
        if (status != ImportStatus.NEEDS_INPUT) {
            throw new IllegalStateException("Input can change only when import needs input");
        }
        if (inputRevision > maxContinuations) {
            throw new IllegalStateException("Maximum input continuations reached");
        }
        inputRevision++;
        inputHash = newInputHash;
        descriptionText = newDescriptionText;
        recipeCheckpointRef = null;
        if (newFile) {
            sourceCheckpointRef = null;
            audioCheckpointRef = null;
            audioOutcome = null;
            transcriptCheckpointRef = null;
        }
        status = ImportStatus.QUEUED;
        nextAttemptAt = clock.instant();
        inputDeadlineAt = newInputDeadline;
        attemptStage = resumeStage;
        stageAttempts = 0;
        errorCode = null;
        leaseOwner = null;
        leaseUntil = null;
        updatedAt = nextAttemptAt;
    }

    public void awaitInput(String error, Instant inputDeadline, Clock clock) {
        if (!isActiveStage(status)) {
            throw new IllegalStateException("Only active imports can await input");
        }
        transitionTo(ImportStatus.NEEDS_INPUT, clock);
        errorCode = error;
        inputDeadlineAt = inputDeadline;
        leaseOwner = null;
        leaseUntil = null;
    }

    public void fail(String error, Clock clock) {
        if (!isActiveStage(status) && status != ImportStatus.RETRY_WAIT) {
            throw new IllegalStateException("Only active imports can fail");
        }
        errorCode = error;
        transitionTo(ImportStatus.FAILED, clock);
    }

    public void expire(Clock clock) {
        if (!isExpiredAt(clock.instant())) {
            throw new IllegalStateException("Import deadline has not expired");
        }
        errorCode = "IMPORT_DEADLINE_EXPIRED";
        transitionTo(ImportStatus.EXPIRED, clock);
    }

    public boolean isExpiredAt(Instant now) {
        if (isTerminal(status)) {
            return false;
        }
        if ((status == ImportStatus.AWAITING_UPLOAD || status == ImportStatus.NEEDS_INPUT)
                && inputDeadlineAt != null && !inputDeadlineAt.isAfter(now)) {
            return true;
        }
        return processingDeadlineAt != null && !processingDeadlineAt.isAfter(now);
    }

    public void checkpoint(ImportStage stage, String reference, Clock clock) {
        if (status != ImportStatus.valueOf(stage.name())) {
            throw new IllegalStateException("Checkpoint stage does not match status");
        }
        switch (stage) {
            case RESOLVING -> sourceCheckpointRef = reference;
            case EXTRACTING_AUDIO -> audioCheckpointRef = reference;
            case TRANSCRIBING -> transcriptCheckpointRef = reference;
            case EXTRACTING_RECIPE, VALIDATING -> recipeCheckpointRef = reference;
        }
        updatedAt = clock.instant();
    }

    public void recordAudioOutcome(AudioOutcome outcome, Clock clock) {
        if (status != ImportStatus.EXTRACTING_AUDIO) {
            throw new IllegalStateException("Audio outcome stage does not match status");
        }
        audioOutcome = outcome;
        updatedAt = clock.instant();
    }

    public void completeStage(
            ImportStage stage,
            ImportStatus nextStatus,
            Clock clock) {
        if (status != ImportStatus.valueOf(stage.name())) {
            throw new IllegalStateException("Stage does not match import status");
        }
        transitionTo(nextStatus, clock);
        leaseOwner = null;
        leaseUntil = null;
    }

    private boolean isActiveStage(ImportStatus value) {
        return value == ImportStatus.RESOLVING || value == ImportStatus.EXTRACTING_AUDIO
                || value == ImportStatus.TRANSCRIBING || value == ImportStatus.EXTRACTING_RECIPE
                || value == ImportStatus.VALIDATING;
    }

    private boolean isClaimableStatus(ImportStatus value) {
        return value == ImportStatus.QUEUED || value == ImportStatus.RETRY_WAIT
                || isActiveStage(value);
    }

    private boolean isTerminal(ImportStatus value) {
        return value == ImportStatus.READY || value == ImportStatus.REVIEW_REQUIRED
                || value == ImportStatus.FAILED || value == ImportStatus.CANCELLED
                || value == ImportStatus.EXPIRED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getClientRequestId() {
        return clientRequestId;
    }

    public String getInputHash() {
        return inputHash;
    }

    public long getInputRevision() {
        return inputRevision;
    }

    public ImportSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public ImportMediaKind getMediaKind() {
        return mediaKind;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getExpectedSizeBytes() {
        return expectedSizeBytes;
    }

    public String getDescriptionText() {
        return descriptionText;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public ImportStage getResumeStage() {
        return resumeStage;
    }

    public int getAttempts() {
        return attempts;
    }

    public ImportStage getAttemptStage() {
        return attemptStage;
    }

    public int getStageAttempts() {
        return stageAttempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public long getLeaseVersion() {
        return leaseVersion;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public Instant getProcessingDeadlineAt() {
        return processingDeadlineAt;
    }

    public Instant getInputDeadlineAt() {
        return inputDeadlineAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getSourceCheckpointRef() {
        return sourceCheckpointRef;
    }

    public String getAudioCheckpointRef() {
        return audioCheckpointRef;
    }

    public AudioOutcome getAudioOutcome() {
        return audioOutcome;
    }

    public String getTranscriptCheckpointRef() {
        return transcriptCheckpointRef;
    }

    public String getRecipeCheckpointRef() {
        return recipeCheckpointRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
