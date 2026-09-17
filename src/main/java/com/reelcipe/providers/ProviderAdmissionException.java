package com.reelcipe.providers;

import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.ImportFailure;

import java.time.Instant;

public class ProviderAdmissionException extends ImportProcessingException {
    private final Instant retryAfter;

    public ProviderAdmissionException(String errorCode, Instant retryAfter) {
        super(ImportFailure.transientError(errorCode), retryAfter);
        this.retryAfter = retryAfter;
    }

    public Instant retryAfter() {
        return retryAfter;
    }
}
