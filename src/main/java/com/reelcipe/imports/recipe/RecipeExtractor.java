package com.reelcipe.imports.recipe;

import java.util.List;

public interface RecipeExtractor {
    Result extract(RecipeTextSnapshot snapshot);

    Result correct(RecipeTextSnapshot snapshot, String invalidJson, List<String> errors);

    record Result(String candidateJson, Usage usage) {
    }

    record Usage(int inputUnits, int outputUnits) {
    }
}
