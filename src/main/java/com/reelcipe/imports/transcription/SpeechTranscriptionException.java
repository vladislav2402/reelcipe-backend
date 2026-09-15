package com.reelcipe.imports.transcription;

public class SpeechTranscriptionException extends RuntimeException {
    public enum Kind {
        TIMEOUT,
        UNKNOWN,
        INVALID_RESPONSE
    }

    private final Kind kind;

    public SpeechTranscriptionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public SpeechTranscriptionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
