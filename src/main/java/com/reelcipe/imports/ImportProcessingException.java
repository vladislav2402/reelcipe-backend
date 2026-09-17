package com.reelcipe.imports;

import com.reelcipe.imports.domain.ImportFailure;

import java.time.Instant;

public class ImportProcessingException extends RuntimeException {
    private final ImportFailure failure;
    private final Instant retryAfter;

    public ImportProcessingException(ImportFailure failure, Throwable cause) {
        this(failure, cause, null);
    }

    public ImportProcessingException(
            ImportFailure failure,
            Throwable cause,
            Instant retryAfter) {
        super(failure.errorCode(), cause);
        this.failure = failure;
        this.retryAfter = retryAfter;
    }

    public ImportProcessingException(ImportFailure failure) {
        this(failure, null, null);
    }

    public ImportProcessingException(ImportFailure failure, Instant retryAfter) {
        this(failure, null, retryAfter);
    }

    public ImportFailure failure() {
        return failure;
    }

    public Instant retryAfter() {
        return retryAfter;
    }
}
