package com.reelcipe.imports.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.recipe.domain.RecipeCandidateRepository;
import com.reelcipe.imports.transcription.domain.TranscriptionRepository;
import com.reelcipe.imports.transcription.domain.TranscriptionSegmentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecipeExtractionPipelineServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    @Test
    void performsOnlyOneBoundedCorrectiveRetryAndKeepsSameSnapshot() {
        UUID importId = UuidV7.randomUuid();
        ImportJob job = job(importId, "Boil pasta.");
        ImportLease lease = lease(importId);
        ImportJobRepository jobs = mock(ImportJobRepository.class);
        TranscriptionRepository transcriptions = mock(TranscriptionRepository.class);
        TranscriptionSegmentRepository segments = mock(TranscriptionSegmentRepository.class);
        RecipeCandidateRepository candidates = mock(RecipeCandidateRepository.class);
        RecipeExtractionCheckpointPersistence checkpoints = mock(RecipeExtractionCheckpointPersistence.class);
        RecipeCandidateValidator validator = new RecipeCandidateValidator(new ObjectMapper());
        RecipeExtractor extractor = mock(RecipeExtractor.class);
        when(extractor.extract(any())).thenReturn(
                new RecipeExtractor.Result("{\"title\":", new RecipeExtractor.Usage(1, 1)));
        when(extractor.correct(any(), eq("{\"title\":"), any())).thenReturn(
                new RecipeExtractor.Result(validCandidate(), new RecipeExtractor.Usage(2, 3)));
        when(jobs.findFencedForUpdate(
                importId, lease.owner(), lease.leaseVersion(), lease.inputRevision()))
                .thenReturn(Optional.of(job));
        when(candidates.findTopByImportIdAndInputHashOrderByCreatedAtDesc(
                eq(importId), any())).thenReturn(Optional.empty());
        when(checkpoints.start(any(), any())).thenReturn(
                new RecipeExtractionCheckpointPersistence.StartedAttempt(
                        UuidV7.randomUuid(), "mock", "mock-v1"));

        RecipeExtractionPipelineService service = new RecipeExtractionPipelineService(
                jobs,
                transcriptions,
                segments,
                candidates,
                extractor,
                validator,
                checkpoints,
                "en",
                "recipe-extraction-v1",
                "recipe-extraction-v1",
                "b21-v1",
                "mock",
                "mock-v1",
                1);
        service.extract(lease, new ImportLeaseControl());

        verify(extractor, times(1)).extract(any());
        verify(extractor, times(1)).correct(any(), any(), any());
        verify(checkpoints, times(2)).start(eq(lease), any());
        verify(checkpoints).checkpoint(eq(lease), any(), any(), any());
    }

    private ImportJob job(UUID importId, String description) {
        return new ImportJob(
                importId,
                UuidV7.randomUuid(),
                UuidV7.randomUuid(),
                ImportSourceType.LINK,
                "https://example.test/recipe",
                ImportMediaKind.AUDIO,
                null,
                null,
                null,
                description,
                "input-hash",
                ImportStatus.EXTRACTING_RECIPE,
                ImportStage.EXTRACTING_RECIPE,
                NOW.plusSeconds(3600),
                null,
                NOW);
    }

    private ImportLease lease(UUID importId) {
        return new ImportLease(
                importId,
                UuidV7.randomUuid(),
                "worker-1",
                1,
                1,
                ImportStage.EXTRACTING_RECIPE,
                NOW.plusSeconds(60));
    }

    private String validCandidate() {
        return "{\"title\":\"Pasta\",\"language\":\"EN\","
                + "\"description\":\"Boil pasta.\",\"ingredients\":[],"
                + "\"steps\":[{\"position\":1,\"text\":\"Boil pasta.\","
                + "\"evidence\":[{\"source\":\"USER_TEXT\","
                + "\"segmentIndex\":null,\"quote\":\"Boil pasta.\"}]}]}";
    }
}
