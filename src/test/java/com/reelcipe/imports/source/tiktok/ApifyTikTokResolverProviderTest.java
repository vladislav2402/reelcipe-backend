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

class ApifyTikTokResolverProviderTest {
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/acts/lurkapi~tiktok-scraper-all-in-one/runs", exchange -> {
            String body = """
                    {"data":{"status":"SUCCEEDED","defaultDatasetId":"dataset-1",
                    "defaultKeyValueStoreId":"store-1"}}
                    """;
            respond(exchange, body);
        });
        server.createContext("/datasets/dataset-1/items", exchange -> {
            String body = """
                    [{"webUrl":"https://www.tiktok.com/@cook/video/123",
                    "authorUsername":"cook","title":"Apple crumble",
                    "transcript":"Cut the apples.",
                    "downloadUrl":"https://cdn.example/video.mp4"}]
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
    void startsActorAndMapsVideoResult() {
        ApifyTikTokResolverProvider provider = new ApifyTikTokResolverProvider(
                new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "fixture-token",
                "lurkapi~tiktok-scraper-all-in-one",
                Duration.ofSeconds(2),
                1024 * 1024);

        TikTokResolverProvider.Resolution result = provider.resolve(
                URI.create("https://vt.tiktok.com/short"));

        assertThat(result.canonicalUrl()).isEqualTo(
                URI.create("https://www.tiktok.com/@cook/video/123"));
        assertThat(result.authorName()).isEqualTo("cook");
        assertThat(result.descriptionStatus()).isEqualTo("AVAILABLE");
        assertThat(result.audioStatus()).isEqualTo("AVAILABLE");
        assertThat(result.videoUrl()).isEqualTo(URI.create("https://cdn.example/video.mp4"));
        assertThat(result.transcript()).isEqualTo("Cut the apples.");
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
