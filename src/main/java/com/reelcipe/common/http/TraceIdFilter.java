package com.reelcipe.common.http;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestedTraceId = request.getHeader(HEADER_NAME);
        String traceId = requestedTraceId == null || requestedTraceId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedTraceId.trim();
        response.setHeader(HEADER_NAME, traceId);
        request.setAttribute(HEADER_NAME, traceId);
        filterChain.doFilter(request, response);
    }
}
