package com.reelcipe.imports;

import com.reelcipe.imports.domain.ImportJob;
import com.reelcipe.imports.domain.ImportSourceType;
import com.reelcipe.imports.domain.ImportStage;
import com.reelcipe.imports.domain.ImportStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ImportJobTest {
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void validPipelineTransitionsAndRetryPreserveResumeStage() {
        ImportJob job = job(ImportStatus.QUEUED);

        job.transitionTo(ImportStatus.RESOLVING, CLOCK);
        job.scheduleRetry(NOW.plusSeconds(30), "RESOLVER_TIMEOUT", CLOCK);
        assertEquals(ImportStatus.RETRY_WAIT, job.getStatus());
        assertEquals(ImportStage.RESOLVING, job.getResumeStage());

        job.resumeRetry(CLOCK);
        assertEquals(ImportStatus.RESOLVING, job.getStatus());
        job.transitionTo(ImportStatus.EXTRACTING_AUDIO, CLOCK);
        job.transitionTo(ImportStatus.TRANSCRIBING, CLOCK);
        job.transitionTo(ImportStatus.EXTRACTING_RECIPE, CLOCK);
        job.transitionTo(ImportStatus.VALIDATING, CLOCK);
        job.transitionTo(ImportStatus.REVIEW_REQUIRED, CLOCK);

        assertEquals(NOW, job.getCompletedAt());
    }

    @Test
    void forbiddenTransitionAndTerminalMutationAreRejected() {
        ImportJob job = job(ImportStatus.QUEUED);

        assertThrows(IllegalStateException.class, () -> job.transitionTo(ImportStatus.READY, CLOCK));
        job.transitionTo(ImportStatus.RESOLVING, CLOCK);
        job.transitionTo(ImportStatus.FAILED, CLOCK);
        assertThrows(IllegalStateException.class, () -> job.transitionTo(ImportStatus.QUEUED, CLOCK));
        job.retry(CLOCK);
        assertEquals(ImportStatus.QUEUED, job.getStatus());
    }

    @Test
    void textInputKeepsTranscriptButNewFileInvalidatesIt() {
        ImportJob job = job(ImportStatus.QUEUED);
        job.transitionTo(ImportStatus.RESOLVING, CLOCK);
        job.transitionTo(ImportStatus.EXTRACTING_AUDIO, CLOCK);
        job.transitionTo(ImportStatus.TRANSCRIBING, CLOCK);
        job.transitionTo(ImportStatus.EXTRACTING_RECIPE, CLOCK);
        job.checkpoint(ImportStage.EXTRACTING_RECIPE, "recipe-v1", CLOCK);
        job.transitionTo(ImportStatus.NEEDS_INPUT, CLOCK);
        assertThrows(IllegalStateException.class, () -> job.checkpoint(ImportStage.RESOLVING, "source", CLOCK));

        job.changeInputRevision("text-v2", false, CLOCK);
        assertEquals(2, job.getInputRevision());
        assertEquals(ImportStatus.QUEUED, job.getStatus());
        assertNull(job.getRecipeCheckpointRef());
    }

    @Test
    void claimChangesQueuedJobToItsResumeStageAndFencesIt() {
        ImportJob job = job(ImportStatus.QUEUED);

        job.claimForProcessing("worker-a", NOW.plusSeconds(90), CLOCK);

        assertEquals(ImportStatus.RESOLVING, job.getStatus());
        assertEquals("worker-a", job.getLeaseOwner());
        assertEquals(1, job.getLeaseVersion());
        assertEquals(NOW.plusSeconds(90), job.getLeaseUntil());
        assertNull(job.getNextAttemptAt());
    }

    @Test
    void claimRejectsWaitingForUploadAndTerminalJobs() {
        ImportJob upload = new ImportJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ImportSourceType.UPLOAD,
                null,
                null,
                "video.mp4",
                "video/mp4",
                100L,
                null,
                "hash-v1",
                ImportStatus.AWAITING_UPLOAD,
                ImportStage.RESOLVING,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(86400),
                NOW);

        assertThrows(IllegalStateException.class, () -> upload.claimForProcessing(
                "worker-a", NOW.plusSeconds(90), CLOCK));
    }

    private ImportJob job(ImportStatus status) {
        return new ImportJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ImportSourceType.LINK,
                "https://example.com/video",
                "hash-v1",
                status,
                ImportStage.RESOLVING,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(86400),
                NOW);
    }
}
