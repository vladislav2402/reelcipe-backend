package com.reelcipe.imports.recipe;

import com.reelcipe.imports.domain.AudioOutcome;
import com.reelcipe.imports.transcription.domain.SpeechStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeTextSnapshotTest {
    private static final UUID TRANSCRIPTION_ID = UUID.randomUUID();
    private static final UUID SEGMENT_ID = UUID.randomUUID();

    @Test
    void unchangedInputHasStableHash() {
        RecipeTextSnapshot first = snapshot("Add pasta.");
        RecipeTextSnapshot second = snapshot("Add pasta.");

        assertThat(first.inputHash()).isEqualTo(second.inputHash());
    }

    @Test
    void changingOneInputChangesHash() {
        RecipeTextSnapshot first = snapshot("Add pasta.");
        RecipeTextSnapshot second = snapshot("Add salt.");

        assertThat(first.inputHash()).isNotEqualTo(second.inputHash());
    }

    @Test
    void sourceCoverageKeepsDescriptionAndAudioScenariosSeparate() {
        RecipeTextSnapshot description = snapshotWithoutTranscript("Boil pasta.");
        RecipeTextSnapshot audio = snapshot("Add pasta.");

        assertThat(description.sourceCoverage()).isEqualTo("DESCRIPTION_ONLY");
        assertThat(audio.sourceCoverage()).isEqualTo("AUDIO_ONLY");
        assertThat(description.inputHash()).isNotEqualTo(audio.inputHash());
    }

    private RecipeTextSnapshot snapshot(String text) {
        return new RecipeTextSnapshot(
                TRANSCRIPTION_ID,
                1,
                "transcript-hash",
                List.of(new RecipeTextSnapshot.TranscriptSegment(
                        SEGMENT_ID, 0, 0, 1000, text)),
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
    }

    private RecipeTextSnapshot snapshotWithoutTranscript(String text) {
        return new RecipeTextSnapshot(
                null,
                0,
                null,
                List.of(),
                text,
                null,
                AudioOutcome.DESCRIPTION_ONLY,
                null,
                "en",
                "en",
                "recipe-extraction-v1",
                "recipe-extraction-v1",
                "b21-v1",
                "mock",
                "mock-v1");
    }
}
