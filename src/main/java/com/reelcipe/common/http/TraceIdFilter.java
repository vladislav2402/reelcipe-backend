package com.reelcipe.common.http;

import com.reelcipe.common.UuidV7;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final Pattern SENSITIVE_JSON_VALUE = Pattern.compile(
            "(\"(?:accessToken|refreshToken|password|secret|apiKey|authorization)\"\\s*:\\s*\")"
                    + "((?:\\\\.|[^\"\\\\])*)(\")",
            Pattern.CASE_INSENSITIVE);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestedTraceId = request.getHeader(HEADER_NAME);
        String traceId = requestedTraceId == null || requestedTraceId.isBlank()
                ? UuidV7.randomUuid().toString()
                : requestedTraceId.trim();
        response.setHeader(HEADER_NAME, traceId);
        request.setAttribute(HEADER_NAME, traceId);

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        long startedAt = System.nanoTime();
        Throwable failure = null;
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, traceId)) {
            LOGGER.info("HTTP request {} {}", request.getMethod(), request.getRequestURI());
            try {
                filterChain.doFilter(request, responseWrapper);
            } catch (IOException | ServletException | RuntimeException | Error exception) {
                failure = exception;
                throw exception;
            } finally {
                long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
                String responseBody = responseBody(responseWrapper);
                if (failure == null) {
                    logResponse(request, responseWrapper, durationMillis, responseBody);
                    responseWrapper.copyBodyToResponse();
                } else {
                    LOGGER.warn(
                            "HTTP request failed {} {} -> {} ({} ms), response_body={}, exception={}",
                            request.getMethod(),
                            request.getRequestURI(),
                            responseWrapper.getStatus(),
                            durationMillis,
                            responseBody,
                            failure.getClass().getSimpleName());
                }
            }
        }
    }

    private void logResponse(
            HttpServletRequest request,
            ContentCachingResponseWrapper response,
            long durationMillis,
            String responseBody) {
        String message = "HTTP response {} {} -> {} ({} ms), response_body={}";
        if (response.getStatus() >= 500) {
            LOGGER.error(
                    message,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMillis,
                    responseBody);
        } else if (response.getStatus() >= 400) {
            LOGGER.warn(
                    message,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMillis,
                    responseBody);
        } else {
            LOGGER.info(
                    message,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMillis,
                    responseBody);
        }
    }

    private String responseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0) {
            return "<empty>";
        }
        if (!isTextResponse(response.getContentType())) {
            return "<binary " + content.length + " bytes>";
        }

        Charset charset = StandardCharsets.UTF_8;
        try {
            charset = Charset.forName(response.getCharacterEncoding());
        } catch (IllegalArgumentException ignored) {
            // Fall back to UTF-8 when the response declares an invalid charset.
        }
        String body = new String(content, charset)
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return SENSITIVE_JSON_VALUE.matcher(body).replaceAll("$1[REDACTED]$3");
    }

    private boolean isTextResponse(String contentType) {
        if (contentType == null) {
            return true;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("text/")
                || normalized.contains("json")
                || normalized.contains("xml")
                || normalized.contains("x-www-form-urlencoded");
    }
}
