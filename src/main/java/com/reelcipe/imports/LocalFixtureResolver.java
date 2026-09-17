package com.reelcipe.imports;

import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.source.SourceResolver;
import com.reelcipe.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class LocalFixtureResolver implements SourceResolver {
    private static final String PREFIX = "fixture://";

    private final ImportJobRepository jobs;
    private final MediaAssetRepository assets;
    private final ObjectStorage storage;
    private final Clock clock;
    private final Path fixtureDirectory;

    public LocalFixtureResolver(
            ImportJobRepository jobs,
            MediaAssetRepository assets,
            ObjectStorage storage,
            Clock clock,
            @Value("${app.import.local-fixtures-directory:${user.dir}/.local/fixtures}")
            String fixtureDirectory) {
        this.jobs = jobs;
        this.assets = assets;
        this.storage = storage;
        this.clock = clock;
        this.fixtureDirectory = Path.of(fixtureDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    @Override
    public boolean resolve(ImportLease lease, ImportLeaseControl control) {
        ImportJob job = jobs.findFencedForUpdate(
                        lease.importId(),
                        lease.owner(),
                        lease.leaseVersion(),
                        lease.inputRevision())
                .orElseThrow(() -> new LeaseLostException(lease));
        if (job.getSourceType() != ImportSourceType.LINK
                || job.getSourceUrl() == null
                || !job.getSourceUrl().startsWith(PREFIX)) {
            return false;
        }
        if (!control.isValid()) {
            throw new LeaseLostException(lease);
        }

        Path fixture = fixturePath(job.getSourceUrl());
        if (!Files.isRegularFile(fixture)) {
            throw new ImportProcessingException(ImportFailure.permanent(
                    "LOCAL_FIXTURE_NOT_FOUND"));
        }
        try {
            byte[] content = Files.readAllBytes(fixture);
            String processingKey = processingKey(lease);
            MediaAsset source = assets.findLockedByImportIdAndAssetType(
                            lease.importId(), MediaAssetType.SOURCE)
                    .orElseGet(() -> new MediaAsset(
                            UuidV7.randomUuid(),
                            lease.importId(),
                            lease.userId(),
                            null,
                            MediaAssetType.SOURCE,
                            null,
                            content.length,
                            job.getContentType() == null
                                    ? contentType(fixture)
                                    : job.getContentType(),
                            job.getProcessingDeadlineAt(),
                            clock.instant()));
            if (source.getProcessingKey() == null) {
                try (var input = Files.newInputStream(fixture)) {
                    storage.put(processingKey, input, content.length, source.getContentType());
                }
                source.recordProcessingCopy(processingKey, sha256(content), content.length, clock);
                assets.save(source);
            }
            job.checkpoint(ImportStage.RESOLVING, source.getProcessingKey(), clock);
            job.completeStage(ImportStage.RESOLVING, ImportStatus.EXTRACTING_AUDIO, clock);
            jobs.save(job);
            return true;
        } catch (IOException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("LOCAL_FIXTURE_READ_FAILED"), exception);
        }
    }

    @Override
    public boolean supports(ImportJob job) {
        return job.getSourceType() == ImportSourceType.LINK
                && job.getSourceUrl() != null
                && job.getSourceUrl().startsWith(PREFIX);
    }

    private Path fixturePath(String sourceUrl) {
        String name = sourceUrl.substring(PREFIX.length());
        Path path = fixtureDirectory.resolve(name).normalize();
        if (!path.startsWith(fixtureDirectory) || name.isBlank()) {
            throw new ImportProcessingException(ImportFailure.permanent("LOCAL_FIXTURE_PATH_INVALID"));
        }
        return path;
    }

    private String processingKey(ImportLease lease) {
        return "processing/" + lease.userId() + "/" + lease.importId()
                + "/fixture-source/lease-" + lease.leaseVersion();
    }

    private String contentType(Path fixture) {
        try {
            String type = Files.probeContentType(fixture);
            return type == null ? "application/octet-stream" : type;
        } catch (IOException exception) {
            return "application/octet-stream";
        }
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
