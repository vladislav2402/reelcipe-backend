package com.reelcipe.imports.recipe;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "mock", matchIfMissing = true)
public class MockRecipeExtractor implements RecipeExtractor {
    @Override
    public Result extract(RecipeTextSnapshot snapshot) {
        return result(snapshot);
    }

    @Override
    public Result correct(RecipeTextSnapshot snapshot, String invalidJson, List<String> errors) {
        return result(snapshot);
    }

    private Result result(RecipeTextSnapshot snapshot) {
        List<SourceText> sourceTexts = new ArrayList<>();
        snapshot.transcriptSegments().forEach(segment -> sourceTexts.add(
                new SourceText(segment.text(), "TRANSCRIPT", String.valueOf(segment.index()))));
        if (sourceTexts.isEmpty()) {
            if (snapshot.authorDescription() != null && !snapshot.authorDescription().isBlank()) {
                sourceTexts.add(new SourceText(
                        snapshot.authorDescription().trim(), "AUTHOR_DESCRIPTION", "null"));
            }
            if (snapshot.userText() != null && !snapshot.userText().isBlank()) {
                sourceTexts.add(new SourceText(snapshot.userText().trim(), "USER_TEXT", "null"));
            }
        }
        if (sourceTexts.isEmpty()) {
            throw new RecipeExtractionException(
                    RecipeExtractionException.Kind.UNKNOWN,
                    "Mock LLM has no text source");
        }

        String language = language(snapshot.targetLanguage());
        String title = title(sourceTexts.getFirst().text());
        String description = snapshot.authorDescription() == null
                ? null
                : snapshot.authorDescription().trim();
        StringBuilder json = new StringBuilder();
        json.append("{\"title\":").append(quote(title));
        json.append(",\"language\":").append(quote(language));
        json.append(",\"description\":").append(description == null
                ? "null"
                : quote(description));
        json.append(",\"ingredients\":[");
        if (snapshot.hasTranscript()) {
            json.append("{\"name\":\"pasta\",\"amount\":null,\"amountMax\":null,")
                    .append("\"unit\":null,\"originalText\":")
                    .append(quote(snapshot.transcriptSegments().getFirst().text()))
                    .append(",\"evidence\":[{\"source\":\"TRANSCRIPT\",")
                    .append("\"segmentIndex\":0,\"quote\":")
                    .append(quote(snapshot.transcriptSegments().getFirst().text()))
                    .append("}]}");
        }
        json.append("],\"steps\":[");
        for (int index = 0; index < sourceTexts.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            SourceText sourceText = sourceTexts.get(index);
            json.append("{\"position\":").append(index + 1)
                    .append(",\"text\":").append(quote(sourceText.text()))
                    .append(",\"evidence\":[{\"source\":")
                    .append(quote(sourceText.source())).append(",\"segmentIndex\":")
                    .append(sourceText.segmentIndex()).append(",\"quote\":")
                    .append(quote(sourceText.text())).append("}]}");
        }
        json.append("]}");
        String value = json.toString();
        return new Result(value, new Usage(value.length(), value.length()));
    }

    private String language(String targetLanguage) {
        if (targetLanguage == null) {
            return "OTHER";
        }
        return switch (targetLanguage.toLowerCase()) {
            case "en" -> "EN";
            case "ru" -> "RU";
            case "uk" -> "UK";
            default -> "OTHER";
        };
    }

    private String title(String text) {
        String clean = text.replaceAll("[.!?].*$", "").trim();
        return clean.isBlank() ? "Imported recipe" : clean;
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }

    private record SourceText(String text, String source, String segmentIndex) {
    }
}
