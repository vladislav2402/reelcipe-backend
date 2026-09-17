package com.reelcipe.imports.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.domain.AudioOutcome;
import com.reelcipe.imports.transcription.domain.SpeechStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCandidateValidatorTest {
    private final RecipeTextSnapshot snapshot = new RecipeTextSnapshot(
            UuidV7.randomUuid(),
            1,
            "hash",
            List.of(new RecipeTextSnapshot.TranscriptSegment(
                    UuidV7.randomUuid(), 0, 0, 1000, "Boil pasta.")),
            null,
            null,
            AudioOutcome.AUDIO_READY,
            SpeechStatus.SPEECH,
            "en",
            "en",
            "recipe-extraction-v1",
            "recipe-extraction-v1",
            "b21-v1",
            "mock",
            "mock-v1");

    @Test
    void rejectsTruncatedCandidate() {
        RecipeCandidateValidator validator = new RecipeCandidateValidator(new ObjectMapper());

        assertThat(validator.validate("{\"title\":\"Pasta\"", snapshot).valid())
                .isFalse();
    }

    @Test
    void acceptsDeterministicMockCandidate() {
        RecipeCandidateValidator validator = new RecipeCandidateValidator(new ObjectMapper());
        String json = new MockRecipeExtractor().extract(snapshot).candidateJson();

        assertThat(validator.validate(json, snapshot).errors()).isEmpty();
    }
}
