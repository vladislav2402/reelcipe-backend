package com.reelcipe.imports.source;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedHttpDownloaderTest {
    @TempDir
    Path tempDirectory;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void downloadsMediaWithStreamingByteLimit() throws IOException {
        server.createContext("/audio", exchange -> respond(exchange, 200, "audio/mpeg", "audio"));
        ManagedHttpDownloader downloader = downloader(1024, 3, Duration.ofSeconds(2));

        DownloadedSource result = downloader.download(URI.create(baseUrl + "/audio"));

        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        assertThat(Files.readString(result.file())).isEqualTo("audio");
        delete(result.file());
    }

    @Test
    void rejectsUnexpectedContentType() {
        server.createContext("/page", exchange -> respond(exchange, 200, "text/html", "<html/>"));
        ManagedHttpDownloader downloader = downloader(1024, 3, Duration.ofSeconds(2));

        assertThatThrownBy(() -> downloader.download(URI.create(baseUrl + "/page")))
                .hasMessageContaining("SOURCE_CONTENT_TYPE_UNSUPPORTED");
    }

    @Test
    void enforcesLimitWhileReadingWhenContentLengthIsMissing() {
        server.createContext("/large", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(new byte[2048]);
            }
        });
        ManagedHttpDownloader downloader = downloader(1024, 3, Duration.ofSeconds(2));

        assertThatThrownBy(() -> downloader.download(URI.create(baseUrl + "/large")))
                .hasMessageContaining("SOURCE_SIZE_LIMIT_EXCEEDED");
    }

    @Test
    void validatesEachRedirectBeforeFollowingIt() {
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl + "/audio");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/audio", exchange -> respond(exchange, 200, "video/mp4", "video"));
        ManagedHttpDownloader downloader = downloader(1024, 3, Duration.ofSeconds(2));

        DownloadedSource result = downloader.download(URI.create(baseUrl + "/redirect"));

        assertThat(result.contentType()).isEqualTo("video/mp4");
        delete(result.file());
    }

    @Test
    void enforcesTimeoutWhileReadingBody() {
        server.createContext("/slow", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                Thread.sleep(300);
                output.write("audio".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        ManagedHttpDownloader downloader = downloader(1024, 3, Duration.ofMillis(100));

        assertThatThrownBy(() -> downloader.download(URI.create(baseUrl + "/slow")))
                .hasMessageContaining("SOURCE_DOWNLOAD_TIMEOUT");
    }

    private ManagedHttpDownloader downloader(long maxBytes, int redirects, Duration timeout) {
        UrlSafetyPolicy fixturePolicy = new UrlSafetyPolicy(
                host -> new java.net.InetAddress[]{java.net.InetAddress.getLoopbackAddress()},
                true,
                true);
        return new ManagedHttpDownloader(
                fixturePolicy,
                Duration.ofSeconds(1),
                timeout,
                maxBytes,
                redirects,
                tempDirectory);
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
