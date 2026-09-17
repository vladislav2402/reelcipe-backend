package com.reelcipe.imports.transcription;

import java.time.Instant;

public class SpeechTranscriptionException extends RuntimeException {
    public enum Kind {
        TIMEOUT,
        UNKNOWN,
        INVALID_RESPONSE
    }

    private final Kind kind;
    private final Integer statusCode;
    private final Instant retryAfter;

    public SpeechTranscriptionException(Kind kind, String message) {
        this(kind, message, null, null, null);
    }

    public SpeechTranscriptionException(
            Kind kind,
            String message,
            Integer statusCode,
            Instant retryAfter) {
        this(kind, message, null, statusCode, retryAfter);
    }

    public SpeechTranscriptionException(Kind kind, String message, Throwable cause) {
        this(kind, message, cause, null, null);
    }

    private SpeechTranscriptionException(
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
}
