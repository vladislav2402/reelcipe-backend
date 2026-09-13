package com.reelcipe.common.http;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<ApiProblem.FieldViolation> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiProblem.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request validation failed", request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiProblem> methodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiProblem> notFound(Exception ignored, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", "The requested resource was not found",
                request, List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiProblem> status(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return problem(status, status.getReasonPhrase(), exception.getReason(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> unexpected(Exception ignored, HttpServletRequest request) {
        LOGGER.error("Unhandled API error for {}", request.getRequestURI(), ignored);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "The request could not be completed", request, List.of());
    }

    private ResponseEntity<ApiProblem> problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request,
            List<ApiProblem.FieldViolation> errors) {
        String traceId = (String) request.getAttribute(TraceIdFilter.HEADER_NAME);
        ApiProblem body = new ApiProblem(
                "about:blank",
                title,
                status.value(),
                detail == null || detail.isBlank() ? title : detail,
                request.getRequestURI(),
                traceId,
                errors);
        return ResponseEntity.status(status)
                .header("Content-Type", "application/problem+json")
                .body(body);
    }
}
