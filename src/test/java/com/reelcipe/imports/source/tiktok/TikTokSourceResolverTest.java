package com.reelcipe.imports.source.tiktok;

import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.source.ManagedHttpDownloader;
import com.reelcipe.imports.source.SourceResolutionPersistence;
import com.reelcipe.imports.source.UrlSafetyPolicy;
import com.reelcipe.storage.ObjectStorage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TikTokSourceResolverTest {
    @TempDir
    Path tempDirectory;

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/video", exchange -> {
            byte[] bytes = "video-fixture".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "video/mp4");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void storesProviderVideoAsNormalSourceAndPreservesAttribution() throws IOException {
        UUID importId = UuidV7.randomUuid();
        UUID userId = UuidV7.randomUuid();
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        ImportJob job = new ImportJob(
                importId,
                userId,
                UuidV7.randomUuid(),
                ImportSourceType.LINK,
                "https://www.tiktok.com/@cook/video/123",
                ImportMediaKind.VIDEO,
                null,
                null,
                null,
                "Use less sugar.",
                "input-hash",
                ImportStatus.RESOLVING,
                ImportStage.RESOLVING,
                now.plusSeconds(3600),
                null,
                now);
        ImportLease lease = new ImportLease(
                importId,
                userId,
                "worker",
                1,
                1,
                ImportStage.RESOLVING,
                now.plusSeconds(60));
        ImportJobRepository jobs = mock(ImportJobRepository.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        SourceResolutionPersistence persistence = mock(SourceResolutionPersistence.class);
        when(jobs.findFencedForUpdate(importId, lease.owner(), 1, 1))
                .thenReturn(Optional.of(job));
        URI mediaUrl = URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/video");
        TikTokResolverProvider provider = sourceUrl -> new TikTokResolverProvider.Resolution(
                URI.create("https://www.tiktok.com/@cook/video/123"),
                "Cook",
                URI.create("https://www.tiktok.com/@cook"),
                "Apple crumble",
                "AVAILABLE",
                "AVAILABLE",
                "VIDEO_FOR_AUDIO",
                null,
                mediaUrl);
        ManagedHttpDownloader downloader = new ManagedHttpDownloader(
                new UrlSafetyPolicy(
                        host -> new java.net.InetAddress[]{java.net.InetAddress.getLoopbackAddress()},
                        true,
                        true),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                1024,
                2,
                tempDirectory);
        TikTokSourceResolver resolver = new TikTokSourceResolver(
                jobs,
                storage,
                persistence,
                downloader,
                provider,
                true);

        resolver.resolve(lease, new ImportLeaseControl());

        verify(storage).put(
                any(String.class),
                any(),
                eq((long) "video-fixture".length()),
                eq("video/mp4"));
        verify(persistence).media(
                eq(lease),
                any(String.class),
                any(String.class),
                eq((long) "video-fixture".length()),
                eq("video/mp4"),
                any());
    }
}
