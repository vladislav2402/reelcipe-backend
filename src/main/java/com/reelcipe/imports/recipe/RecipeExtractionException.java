package com.reelcipe.imports.recipe;

public class RecipeExtractionException extends RuntimeException {
    private final Kind kind;

    public RecipeExtractionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public RecipeExtractionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        TIMEOUT,
        UNKNOWN
    }
}
