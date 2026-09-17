package com.reelcipe.imports.transcription;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroqSpeechTranscriberTest {
    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsVerboseMultipartRequestAndMapsSegments() throws Exception {
        server.createContext("/audio/transcriptions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("whisper-large-v3-turbo");
            assertThat(body).contains("verbose_json");
            assertThat(body).contains("timestamp_granularities[]");
            assertThat(body).contains("filename=\"normalized.flac\"");
            respond(exchange, 200, """
                    {
                      "id": "req_b25",
                      "language": "uk",
                      "duration": 2.4,
                      "segments": [
                        {"start": 0.25, "end": 1.75, "text": "Додай пасту."}
                      ]
                    }
                    """);
        });
        server.start();

        SpeechTranscriber.Result result = transcriber().transcribe(
                new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}),
                new SpeechTranscriber.Request(
                        "hash",
                        3,
                        "normalized.flac",
                        "audio/flac",
                        "uk"));

        assertThat(result.providerRequestId()).isEqualTo("req_b25");
        assertThat(result.language()).isEqualTo("uk");
        assertThat(result.speechStatus().name()).isEqualTo("SPEECH");
        assertThat(result.segments()).containsExactly(
                new SpeechTranscriber.Segment(250, 1750, "Додай пасту."));
        assertThat(result.usage().inputSeconds()).isEqualTo(3);
    }

    @Test
    void maps429AndRetryAfterWithoutExposingTheApiKey() throws Exception {
        server.createContext("/audio/transcriptions", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "7");
            respond(exchange, 429, "provider error secret=must-not-leak");
        });
        server.start();

        assertThatThrownBy(() -> transcriber().transcribe(
                new java.io.ByteArrayInputStream(new byte[] {1}),
                new SpeechTranscriber.Request("hash", 1)))
                .isInstanceOfSatisfying(
                        SpeechTranscriptionException.class,
                        exception -> {
                            assertThat(exception.statusCode()).isEqualTo(429);
                            assertThat(exception.retryAfter()).isEqualTo(NOW.plusSeconds(7));
                            assertThat(exception.getMessage()).doesNotContain("secret");
                        });
    }

    @Test
    void mapsProvider5xxToRetryableException() throws Exception {
        server.createContext("/audio/transcriptions", exchange -> respond(exchange, 503, "unavailable"));
        server.start();

        assertThatThrownBy(() -> transcriber().transcribe(
                new java.io.ByteArrayInputStream(new byte[] {1}),
                new SpeechTranscriber.Request("hash", 1)))
                .isInstanceOfSatisfying(
                        SpeechTranscriptionException.class,
                        exception -> assertThat(exception.statusCode()).isEqualTo(503));
    }

    private GroqSpeechTranscriber transcriber() {
        return new GroqSpeechTranscriber(
                JsonMapper.builder().build(),
                URI.create("http://localhost:" + server.getAddress().getPort()
                        + "/audio/transcriptions"),
                "test-secret",
                "whisper-large-v3-turbo",
                Duration.ofSeconds(5),
                1024,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
