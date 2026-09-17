package com.reelcipe.imports.recipe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeFinalizationServiceTest {
    @Test
    void restoresTranscriptionLanguageWhenRebuildingRecipeSnapshot() {
        RecipeFinalizationService.SnapshotLanguages languages =
                RecipeFinalizationService.snapshotLanguages("uk", "en");

        assertThat(languages.sourceLanguage()).isEqualTo("uk");
        assertThat(languages.targetLanguage()).isEqualTo("en");
    }

    @Test
    void usesTargetLanguageForDescriptionOnlySnapshot() {
        RecipeFinalizationService.SnapshotLanguages languages =
                RecipeFinalizationService.snapshotLanguages(null, "en");

        assertThat(languages.sourceLanguage()).isEqualTo("en");
        assertThat(languages.targetLanguage()).isEqualTo("en");
    }
}
