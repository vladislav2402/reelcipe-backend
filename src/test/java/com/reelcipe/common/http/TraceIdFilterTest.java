package com.reelcipe.common.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TraceIdFilterTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(TraceIdFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void logsCompletedRequestWithSharedTraceId() throws ServletException, IOException {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/recipes");
        request.addHeader(TraceIdFilter.HEADER_NAME, " trace-123 ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertEquals("trace-123", MDC.get(TraceIdFilter.MDC_KEY));
            ((HttpServletResponse) servletResponse).setStatus(201);
            servletResponse.setContentType("application/json");
            servletResponse.getWriter().write("{\"id\":\"recipe-1\"}");
        });

        assertEquals("trace-123", response.getHeader(TraceIdFilter.HEADER_NAME));
        assertEquals("trace-123", request.getAttribute(TraceIdFilter.HEADER_NAME));
        assertEquals("{\"id\":\"recipe-1\"}", response.getContentAsString());
        assertNull(MDC.get(TraceIdFilter.MDC_KEY));
        assertEquals(2, appender.list.size());

        ILoggingEvent event = appender.list.get(1);
        assertEquals(Level.INFO, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("HTTP response POST /v1/recipes -> 201"));
        assertTrue(event.getFormattedMessage().contains("response_body={\"id\":\"recipe-1\"}"));
    }

    @Test
    void logsErrorResponseBodyAtWarnLevel() throws ServletException, IOException {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/recipes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            ((HttpServletResponse) servletResponse).setStatus(422);
            servletResponse.setContentType("application/problem+json");
            servletResponse.getWriter().write("{\"title\":\"Validation failed\"}");
        });

        assertEquals("{\"title\":\"Validation failed\"}", response.getContentAsString());
        assertEquals(2, appender.list.size());
        ILoggingEvent event = appender.list.get(1);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("HTTP response POST /v1/recipes -> 422"));
        assertTrue(event.getFormattedMessage().contains(
                "response_body={\"title\":\"Validation failed\"}"));
    }

    @Test
    void redactsTokensFromLoggedResponseBody() throws ServletException, IOException {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/dev");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json");
            servletResponse.getWriter().write(
                    "{\"accessToken\":\"access-secret\",\"refreshToken\":\"refresh-secret\"}");
        });

        assertEquals(
                "{\"accessToken\":\"access-secret\",\"refreshToken\":\"refresh-secret\"}",
                response.getContentAsString());
        ILoggingEvent event = appender.list.get(1);
        assertTrue(event.getFormattedMessage().contains(
                "response_body={\"accessToken\":\"[REDACTED]\",\"refreshToken\":\"[REDACTED]\"}"));
    }

    @Test
    void logsRequestThatFailsBeforeAResponseIsProduced() {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/recipes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ServletException.class, () -> filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    throw new ServletException("boom");
                }));

        assertEquals(2, appender.list.size());
        ILoggingEvent event = appender.list.get(1);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("HTTP request failed GET /v1/recipes"));
        assertTrue(event.getFormattedMessage().contains("exception=ServletException"));
        assertNull(MDC.get(TraceIdFilter.MDC_KEY));
    }
}
