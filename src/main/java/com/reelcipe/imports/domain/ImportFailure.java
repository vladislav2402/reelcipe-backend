package com.reelcipe.imports.domain;

public record ImportFailure(ImportFailureKind kind, String errorCode) {
    public ImportFailure {
        if (kind == null || errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("Import failure kind and code are required");
        }
    }

    public static ImportFailure transientError(String errorCode) {
        return new ImportFailure(ImportFailureKind.TRANSIENT, errorCode);
    }

    public static ImportFailure needsInput(String errorCode) {
        return new ImportFailure(ImportFailureKind.NEEDS_INPUT, errorCode);
    }

    public static ImportFailure permanent(String errorCode) {
        return new ImportFailure(ImportFailureKind.PERMANENT, errorCode);
    }
}
