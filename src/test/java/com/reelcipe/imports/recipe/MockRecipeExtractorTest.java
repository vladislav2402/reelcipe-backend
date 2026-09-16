package com.reelcipe.imports.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.imports.domain.AudioOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockRecipeExtractorTest {
    @Test
    void returnsSchemaCompatibleTextOnlyCandidateWithoutMediaFields() throws Exception {
        RecipeTextSnapshot snapshot = new RecipeTextSnapshot(
                null,
                0,
                null,
                List.of(),
                "Boil pasta and season with salt.",
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
        String json = new MockRecipeExtractor().extract(snapshot).candidateJson();

        assertThat(json).doesNotContain("media", "image", "importId", "userId");
        assertThat(new ObjectMapper().readTree(json).get("ingredients").size()).isZero();
    }
}
