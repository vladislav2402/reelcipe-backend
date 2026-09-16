package com.reelcipe.imports.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.recipes.domain.RecipeLanguage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class RecipeCandidateSemanticValidator {
    private final ObjectMapper mapper;
    private final RecipeCandidateValidator schemaValidator;

    public RecipeCandidateSemanticValidator(
            ObjectMapper mapper,
            RecipeCandidateValidator schemaValidator) {
        this.mapper = mapper;
        this.schemaValidator = schemaValidator;
    }

    public Result validate(String json, RecipeTextSnapshot snapshot) {
        RecipeCandidateValidator.Validation schema = schemaValidator.validate(json, snapshot);
        if (!schema.valid()) {
            return Result.invalid(schema.errors());
        }
        try {
            JsonNode root = mapper.readTree(json);
            Document document = readDocument(root, snapshot);
            if (document.steps().isEmpty()) {
                return Result.needsInput("RECIPE_HAS_NO_STEPS");
            }
            boolean reviewRequired = document.ingredients().isEmpty()
                    || document.ingredients().stream().anyMatch(ingredient -> ingredient.amount() == null);
            return new Result(document, reviewRequired, List.of(), false);
        } catch (SemanticValidationException exception) {
            return Result.invalid(List.of(exception.getMessage()));
        } catch (IOException | RuntimeException exception) {
            return Result.invalid(List.of("Candidate JSON could not be read"));
        }
    }

    private Document readDocument(JsonNode root, RecipeTextSnapshot snapshot) {
        RecipeLanguage language = language(root.get("language").textValue());
        List<Ingredient> ingredients = new ArrayList<>();
        for (JsonNode node : root.get("ingredients")) {
            BigDecimal amount = decimal(node.get("amount"));
            BigDecimal amountMax = decimal(node.get("amountMax"));
            if (amount != null && amountMax != null && amount.compareTo(amountMax) > 0) {
                throw invalid("Ingredient amount range is inverted");
            }
            ingredients.add(new Ingredient(
                    node.get("name").textValue(),
                    amount,
                    amountMax,
                    nullableText(node.get("unit")),
                    nullableText(node.get("originalText")),
                    readEvidence(node.get("evidence"), snapshot)));
        }
        List<Step> steps = new ArrayList<>();
        for (JsonNode node : root.get("steps")) {
            steps.add(new Step(
                    node.get("position").asInt(),
                    node.get("text").textValue(),
                    readEvidence(node.get("evidence"), snapshot)));
        }
        return new Document(
                root.get("title").textValue(),
                language,
                nullableText(root.get("description")),
                List.copyOf(ingredients),
                List.copyOf(steps));
    }

    private List<Evidence> readEvidence(JsonNode nodes, RecipeTextSnapshot snapshot) {
        List<Evidence> evidence = new ArrayList<>();
        for (JsonNode node : nodes) {
            String source = node.get("source").textValue();
            Integer segmentIndex = node.get("segmentIndex").isNull()
                    ? null
                    : node.get("segmentIndex").asInt();
            String quote = node.get("quote").textValue();
            if (segmentIndex != null) {
                if (!"TRANSCRIPT".equals(source)) {
                    throw invalid("Only transcript evidence may have a segment index");
                }
                RecipeTextSnapshot.TranscriptSegment segment = findSegment(
                        snapshot, segmentIndex);
                if (!contains(segment.text(), quote)) {
                    throw invalid("Transcript evidence quote is not in its segment");
                }
                evidence.add(new Evidence(
                        source,
                        snapshot.transcriptionId(),
                        segment.id(),
                        segment.index(),
                        quote,
                        segment.startMs(),
                        segment.endMs()));
            } else {
                String sourceText = sourceText(snapshot, source);
                if (!contains(sourceText, quote)) {
                    throw invalid("Text evidence quote is not in its source");
                }
                evidence.add(new Evidence(source, null, null, null, quote, null, null));
            }
        }
        if (evidence.isEmpty()) {
            throw invalid("Every recipe value needs evidence");
        }
        return List.copyOf(evidence);
    }

    private RecipeTextSnapshot.TranscriptSegment findSegment(
            RecipeTextSnapshot snapshot,
            int segmentIndex) {
        return snapshot.transcriptSegments().stream()
                .filter(segment -> segment.index() == segmentIndex)
                .findFirst()
                .orElseThrow(() -> invalid("Evidence points to an unknown transcript segment"));
    }

    private String sourceText(RecipeTextSnapshot snapshot, String source) {
        return switch (source) {
            case "AUTHOR_DESCRIPTION" -> snapshot.authorDescription();
            case "USER_TEXT" -> snapshot.userText();
            default -> null;
        };
    }

    private boolean contains(String source, String quote) {
        return source != null && quote != null
                && source.toLowerCase(Locale.ROOT).contains(quote.toLowerCase(Locale.ROOT));
    }

    private RecipeLanguage language(String value) {
        try {
            return RecipeLanguage.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("Candidate language is not supported");
        }
    }

    private BigDecimal decimal(JsonNode node) {
        return node == null || node.isNull() ? null : node.decimalValue();
    }

    private String nullableText(JsonNode node) {
        return node == null || node.isNull() ? null : node.textValue();
    }

    private SemanticValidationException invalid(String message) {
        return new SemanticValidationException(message);
    }

    public record Result(
            Document document,
            boolean reviewRequired,
            List<String> errors,
            boolean needsInput) {
        static Result invalid(List<String> errors) {
            return new Result(null, false, List.copyOf(errors), false);
        }

        static Result needsInput(String error) {
            return new Result(null, false, List.of(error), true);
        }

        public boolean valid() {
            return document != null && errors.isEmpty() && !needsInput;
        }
    }

    public record Document(
            String title,
            RecipeLanguage language,
            String description,
            List<Ingredient> ingredients,
            List<Step> steps) {
    }

    public record Ingredient(
            String name,
            BigDecimal amount,
            BigDecimal amountMax,
            String unit,
            String originalText,
            List<Evidence> evidence) {
    }

    public record Step(int position, String text, List<Evidence> evidence) {
    }

    public record Evidence(
            String sourceType,
            java.util.UUID transcriptionId,
            java.util.UUID segmentId,
            Integer segmentIndex,
            String quote,
            Long startMs,
            Long endMs) {
    }

    private static final class SemanticValidationException extends RuntimeException {
        private SemanticValidationException(String message) {
            super(message);
        }
    }
}
