package com.reelcipe.imports.recipe;

import java.time.Instant;

public class RecipeExtractionException extends RuntimeException {
    private final Kind kind;
    private final Integer statusCode;
    private final Instant retryAfter;

    public RecipeExtractionException(Kind kind, String message) {
        this(kind, message, null, null, null);
    }

    public RecipeExtractionException(
            Kind kind,
            String message,
            Integer statusCode,
            Instant retryAfter) {
        this(kind, message, null, statusCode, retryAfter);
    }

    public RecipeExtractionException(Kind kind, String message, Throwable cause) {
        this(kind, message, cause, null, null);
    }

    private RecipeExtractionException(
            Kind kind,
            String message,
            Throwable cause,
            Integer statusCode,
            Instant retryAfter) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    public Kind kind() {
        return kind;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public Instant retryAfter() {
        return retryAfter;
    }

    public enum Kind {
        TIMEOUT,
        UNKNOWN,
        REFUSAL,
        INVALID_RESPONSE
    }
}
