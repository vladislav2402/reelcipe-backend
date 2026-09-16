package com.reelcipe.imports.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Component
public class RecipeCandidateValidator {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "title", "language", "description", "ingredients", "steps");
    private static final Set<String> INGREDIENT_FIELDS = Set.of(
            "name", "amount", "amountMax", "unit", "originalText", "evidence");
    private static final Set<String> STEP_FIELDS = Set.of("position", "text", "evidence");
    private static final Set<String> EVIDENCE_FIELDS = Set.of("source", "segmentIndex", "quote");
    private static final Set<String> LANGUAGES = Set.of("EN", "RU", "UK", "OTHER");
    private static final Set<String> SOURCES = Set.of(
            "TRANSCRIPT", "AUTHOR_DESCRIPTION", "USER_TEXT");

    private final ObjectMapper mapper;

    public RecipeCandidateValidator(ObjectMapper mapper) {
        this.mapper = mapper;
        verifySchemaIsPackaged();
    }

    public Validation validate(String candidateJson, RecipeTextSnapshot snapshot) {
        List<String> errors = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(candidateJson);
            validateRoot(root, errors);
            if (root != null && root.isObject()) {
                validateIngredients(root.get("ingredients"), snapshot, errors);
                validateSteps(root.get("steps"), snapshot, errors);
            }
        } catch (IOException | RuntimeException exception) {
            errors.add("candidate JSON is not valid JSON");
        }
        return new Validation(errors.isEmpty(), List.copyOf(errors));
    }

    private void validateRoot(JsonNode root, List<String> errors) {
        if (root == null || !root.isObject()) {
            errors.add("root must be an object");
            return;
        }
        rejectUnknown(root, ROOT_FIELDS, "root", errors);
        text(root, "title", 1, 200, errors);
        if (!root.has("language") || !root.get("language").isTextual()
                || !LANGUAGES.contains(root.get("language").textValue())) {
            errors.add("language must be one of EN, RU, UK, OTHER");
        }
        JsonNode description = root.get("description");
        if (description == null || !(description.isNull() || description.isTextual())
                || description.isTextual() && description.textValue().length() > 4000) {
            errors.add("description must be a string or null");
        }
        array(root, "ingredients", 100, errors);
        array(root, "steps", 100, errors);
    }

    private void validateIngredients(JsonNode ingredients, RecipeTextSnapshot snapshot, List<String> errors) {
        if (ingredients == null || !ingredients.isArray()) {
            return;
        }
        for (JsonNode ingredient : ingredients) {
            if (!ingredient.isObject()) {
                errors.add("ingredient must be an object");
                continue;
            }
            rejectUnknown(ingredient, INGREDIENT_FIELDS, "ingredient", errors);
            text(ingredient, "name", 1, 200, errors);
            nullableNumber(ingredient, "amount", errors);
            nullableNumber(ingredient, "amountMax", errors);
            nullableText(ingredient, "unit", 64, errors);
            nullableText(ingredient, "originalText", 1000, errors);
            validateEvidence(ingredient.get("evidence"), snapshot, errors);
        }
    }

    private void validateSteps(JsonNode steps, RecipeTextSnapshot snapshot, List<String> errors) {
        if (steps == null || !steps.isArray()) {
            return;
        }
        int expectedPosition = 1;
        for (JsonNode step : steps) {
            if (!step.isObject()) {
                errors.add("step must be an object");
                continue;
            }
            rejectUnknown(step, STEP_FIELDS, "step", errors);
            JsonNode position = step.get("position");
            if (position == null || !position.canConvertToInt()
                    || position.asInt() != expectedPosition) {
                errors.add("steps must have consecutive positions");
            }
            expectedPosition++;
            text(step, "text", 1, 4000, errors);
            validateEvidence(step.get("evidence"), snapshot, errors);
        }
    }

    private void validateEvidence(JsonNode evidence, RecipeTextSnapshot snapshot, List<String> errors) {
        if (evidence == null || !evidence.isArray() || evidence.size() > 10) {
            errors.add("evidence must be an array with at most 10 items");
            return;
        }
        for (JsonNode item : evidence) {
            if (!item.isObject()) {
                errors.add("evidence item must be an object");
                continue;
            }
            rejectUnknown(item, EVIDENCE_FIELDS, "evidence", errors);
            JsonNode source = item.get("source");
            if (source == null || !source.isTextual() || !SOURCES.contains(source.textValue())) {
                errors.add("evidence source is invalid");
            }
            nullableSegmentIndex(item.get("segmentIndex"), snapshot, source, errors);
            text(item, "quote", 1, 1000, errors);
        }
    }

    private void nullableSegmentIndex(
            JsonNode segmentIndex,
            RecipeTextSnapshot snapshot,
            JsonNode source,
            List<String> errors) {
        if (segmentIndex == null || segmentIndex.isNull()) {
            return;
        }
        if (source != null && source.isTextual() && !"TRANSCRIPT".equals(source.textValue())) {
            errors.add("non-transcript evidence cannot have a segment index");
        }
        if (!segmentIndex.canConvertToInt() || segmentIndex.asInt() < 0
                || segmentIndex.asInt() >= snapshot.transcriptSegments().size()) {
            errors.add("evidence segment index is outside the snapshot");
        }
    }

    private void array(JsonNode root, String field, int maxItems, List<String> errors) {
        JsonNode value = root.get(field);
        if (value == null || !value.isArray() || value.size() > maxItems) {
            errors.add(field + " must be an array with at most " + maxItems + " items");
        }
    }

    private void text(JsonNode root, String field, int min, int max, List<String> errors) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual()
                || value.textValue().length() < min || value.textValue().length() > max) {
            errors.add(field + " must be a bounded string");
        }
    }

    private void nullableText(JsonNode root, String field, int max, List<String> errors) {
        JsonNode value = root.get(field);
        if (value == null || !(value.isNull() || value.isTextual())
                || value.isTextual() && value.textValue().length() > max) {
            errors.add(field + " must be a string or null");
        }
    }

    private void nullableNumber(JsonNode root, String field, List<String> errors) {
        JsonNode value = root.get(field);
        if (value == null || !(value.isNull() || value.isNumber())
                || value.isNumber() && value.decimalValue().signum() < 0) {
            errors.add(field + " must be a non-negative number or null");
        }
    }

    private void rejectUnknown(JsonNode node, Set<String> allowed, String name, List<String> errors) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                errors.add(name + " contains an unknown field");
            }
        }
    }

    private void verifySchemaIsPackaged() {
        if (!new ClassPathResource("contracts/recipe-extraction-v1.schema.json").exists()) {
            throw new IllegalStateException("Recipe extraction schema is not packaged");
        }
    }

    public record Validation(boolean valid, List<String> errors) {
    }
}
