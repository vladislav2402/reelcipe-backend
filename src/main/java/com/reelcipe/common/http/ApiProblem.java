package com.reelcipe.common.http;

import java.util.List;

public record ApiProblem(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String traceId,
        List<FieldViolation> errors) {

    public record FieldViolation(String field, String message) {
    }
}
