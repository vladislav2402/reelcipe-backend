package com.reelcipe.imports;

import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.domain.*;
import com.reelcipe.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LocalFixtureResolverTest {
    @Test
    void resolvesFixtureLinkWithoutNetworkAccess(@TempDir Path fixtureDirectory) throws Exception {
        byte[] content = "fixture-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(fixtureDirectory.resolve("recipe.mp4"), content);
        UUID importId = UuidV7.randomUuid();
        UUID userId = UuidV7.randomUuid();
        Instant now = Instant.parse("2026-09-16T00:00:00Z");
        ImportJob job = new ImportJob(
                importId,
                userId,
                UuidV7.randomUuid(),
                ImportSourceType.LINK,
                "fixture://recipe.mp4",
                ImportMediaKind.VIDEO,
                "recipe.mp4",
                "video/mp4",
                null,
                "Add pasta.",
                "input-hash",
                ImportStatus.RESOLVING,
                ImportStage.RESOLVING,
                now.plusSeconds(3600),
                null,
                now);
        ImportLease lease = new ImportLease(
                importId,
                userId,
                "fixture-worker",
                1,
                1,
                ImportStage.RESOLVING,
                now.plusSeconds(60));
        ImportJobRepository jobs = mock(ImportJobRepository.class);
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        when(jobs.findFencedForUpdate(importId, lease.owner(), 1, 1))
                .thenReturn(Optional.of(job));
        when(assets.findLockedByImportIdAndAssetType(eq(importId), any()))
                .thenReturn(Optional.empty());

        LocalFixtureResolver resolver = new LocalFixtureResolver(
                jobs,
                assets,
                storage,
                Clock.fixed(now, ZoneOffset.UTC),
                fixtureDirectory.toString());

        assertThat(resolver.resolve(lease, new ImportLeaseControl())).isTrue();
        assertThat(job.getStatus()).isEqualTo(ImportStatus.EXTRACTING_AUDIO);
        verify(storage).put(
                eq("processing/" + userId + "/" + importId + "/fixture-source/lease-1"),
                any(),
                eq((long) content.length),
                eq("video/mp4"));
        verify(assets).save(any());
        verify(jobs).save(job);
    }

    @Test
    void ignoresNonFixtureLinkAndLeavesStageUntouched() {
        UUID importId = UuidV7.randomUuid();
        UUID userId = UuidV7.randomUuid();
        Instant now = Instant.parse("2026-09-16T00:00:00Z");
        ImportJob job = new ImportJob(
                importId,
                userId,
                UuidV7.randomUuid(),
                ImportSourceType.LINK,
                "https://example.com/recipe",
                null,
                null,
                null,
                null,
                "Add pasta.",
                "input-hash",
                ImportStatus.RESOLVING,
                ImportStage.RESOLVING,
                now.plusSeconds(3600),
                null,
                now);
        ImportLease lease = new ImportLease(
                importId,
                userId,
                "fixture-worker",
                1,
                1,
                ImportStage.RESOLVING,
                now.plusSeconds(60));
        ImportJobRepository jobs = mock(ImportJobRepository.class);
        when(jobs.findFencedForUpdate(importId, lease.owner(), 1, 1))
                .thenReturn(Optional.of(job));

        LocalFixtureResolver resolver = new LocalFixtureResolver(
                jobs,
                mock(MediaAssetRepository.class),
                mock(ObjectStorage.class),
                Clock.fixed(now, ZoneOffset.UTC),
                "unused");

        assertThat(resolver.resolve(lease, new ImportLeaseControl())).isFalse();
        assertThat(job.getStatus()).isEqualTo(ImportStatus.RESOLVING);
    }
}
