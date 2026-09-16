package com.reelcipe.imports.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.imports.domain.AudioOutcome;
import com.reelcipe.imports.transcription.domain.SpeechStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCandidateSemanticValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RecipeCandidateSemanticValidator validator =
            new RecipeCandidateSemanticValidator(
                    mapper,
                    new RecipeCandidateValidator(mapper));

    @Test
    void acceptsEvidenceOnlyFromTheSnapshotSource() {
        UUID segmentId = UUID.randomUUID();
        RecipeTextSnapshot snapshot = new RecipeTextSnapshot(
                UUID.randomUUID(),
                1,
                "transcript-hash",
                List.of(new RecipeTextSnapshot.TranscriptSegment(
                        segmentId, 0, 0, 1800, "Add pasta to boiling water.")),
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

        RecipeCandidateSemanticValidator.Result result = validator.validate(
                audioCandidate(), snapshot);

        assertThat(result.valid()).isTrue();
        assertThat(result.document().ingredients().getFirst().evidence().getFirst().segmentId())
                .isEqualTo(segmentId);
    }

    @Test
    void rejectsEvidenceQuoteThatIsNotInTheTranscript() {
        RecipeTextSnapshot snapshot = new RecipeTextSnapshot(
                UUID.randomUUID(),
                1,
                "transcript-hash",
                List.of(new RecipeTextSnapshot.TranscriptSegment(
                        UUID.randomUUID(), 0, 0, 1800, "Add pasta.")),
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

        RecipeCandidateSemanticValidator.Result result = validator.validate(
                audioCandidate().replace("Add pasta to boiling water.", "Add salt."), snapshot);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("quote"));
    }

    private String audioCandidate() {
        return "{\"title\":\"Pasta\",\"language\":\"EN\","
                + "\"description\":null,\"ingredients\":[{\"name\":\"pasta\","
                + "\"amount\":null,\"amountMax\":null,\"unit\":null,"
                + "\"originalText\":\"Add pasta to boiling water.\","
                + "\"evidence\":[{\"source\":\"TRANSCRIPT\",\"segmentIndex\":0,"
                + "\"quote\":\"Add pasta to boiling water.\"}]}],"
                + "\"steps\":[{\"position\":1,\"text\":\"Add pasta to boiling water.\","
                + "\"evidence\":[{\"source\":\"TRANSCRIPT\",\"segmentIndex\":0,"
                + "\"quote\":\"Add pasta to boiling water.\"}]}]}";
    }
}
