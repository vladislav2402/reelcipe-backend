package com.reelcipe.imports.source.tiktok;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RapidApiTikTokResolverProviderTest {
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/vid/index", exchange -> {
            String body = """
                    {"video":["https://cdn.example/video.mp4"],
                    "description":["Apple crumble"],"author":"cook"}
                    """;
            respond(exchange, body);
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void requestsVideoAndMapsRapidApiResponse() {
        RapidApiTikTokResolverProvider provider = new RapidApiTikTokResolverProvider(
                new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "fixture-key",
                "fixture-host",
                Duration.ofSeconds(2),
                1024 * 1024);

        TikTokResolverProvider.Resolution result = provider.resolve(
                URI.create("https://www.tiktok.com/@cook/video/123"));

        assertThat(result.videoUrl()).isEqualTo(URI.create("https://cdn.example/video.mp4"));
        assertThat(result.authorName()).isEqualTo("cook");
        assertThat(result.descriptionStatus()).isEqualTo("AVAILABLE");
        assertThat(result.audioStatus()).isEqualTo("AVAILABLE");
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
