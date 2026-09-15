package com.reelcipe.imports.audio;

public class AudioExtractionException extends RuntimeException {
    private final Kind kind;

    public AudioExtractionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AudioExtractionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        TIMEOUT,
        INVALID_MEDIA,
        TOOL_UNAVAILABLE,
        OUTPUT_INVALID
    }
}
