package com.reelcipe.imports.domain;

public enum ImportStatus {
    AWAITING_UPLOAD,
    QUEUED,
    RESOLVING,
    EXTRACTING_AUDIO,
    TRANSCRIBING,
    EXTRACTING_RECIPE,
    VALIDATING,
    RETRY_WAIT,
    NEEDS_INPUT,
    READY,
    REVIEW_REQUIRED,
    FAILED,
    CANCELLED,
    EXPIRED
}
