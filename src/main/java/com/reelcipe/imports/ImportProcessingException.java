package com.reelcipe.imports;

import com.reelcipe.imports.domain.ImportFailure;

public class ImportProcessingException extends RuntimeException {
    private final ImportFailure failure;

    public ImportProcessingException(ImportFailure failure, Throwable cause) {
        super(failure.errorCode(), cause);
        this.failure = failure;
    }

    public ImportProcessingException(ImportFailure failure) {
        super(failure.errorCode());
        this.failure = failure;
    }

    public ImportFailure failure() {
        return failure;
    }
}
