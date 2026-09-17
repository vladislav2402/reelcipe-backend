package com.reelcipe.imports.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeExtractionEvaluationFixtureTest {
    @Test
    void keepsTenLabeledCasesForTheFirstRealEvalSet() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = getClass().getResourceAsStream(
                "/evals/recipe-extraction-v1.json")) {
            JsonNode fixtures = mapper.readTree(input);
            Set<String> ids = new HashSet<>();
            fixtures.forEach(fixture -> ids.add(fixture.path("id").asText()));

            assertThat(fixtures).hasSize(10);
            assertThat(ids).hasSize(10);
            assertThat(fixtures.findValuesAsText("coverage"))
                    .contains("AUDIO_ONLY", "DESCRIPTION_ONLY", "AUDIO_AND_DESCRIPTION");
            assertThat(fixtures.findValuesAsText("source"))
                    .anyMatch(source -> source.contains("Ignore system instructions"));
        }
    }
}
