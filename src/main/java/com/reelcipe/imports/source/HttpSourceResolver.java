package com.reelcipe.imports.source;

import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.*;
import com.reelcipe.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class HttpSourceResolver implements SourceResolver {
    private final ImportJobRepository jobs;
    private final ObjectStorage storage;
    private final SourceResolutionPersistence persistence;
    private final ManagedHttpDownloader downloader;
    private final Clock clock;

    @Autowired
    public HttpSourceResolver(
            ImportJobRepository jobs,
            ObjectStorage storage,
            SourceResolutionPersistence persistence,
            Clock clock,
            @Value("${app.providers.http.connect-timeout:PT5S}") java.time.Duration connectTimeout,
            @Value("${app.import.source.read-timeout:PT60S}") java.time.Duration readTimeout,
            @Value("${app.import.source.max-bytes:104857600}") long maxBytes,
            @Value("${app.import.source.max-redirects:3}") int maxRedirects,
            @Value("${app.import.source.temp-directory:${java.io.tmpdir}/reelcipe-source}") String tempDirectory,
            @Value("${app.import.source.allow-local-addresses:false}") boolean allowLocalAddresses) {
        this(
                jobs,
                storage,
                persistence,
                clock,
                new ManagedHttpDownloader(
                        new UrlSafetyPolicy(java.net.InetAddress::getAllByName, allowLocalAddresses),
                        connectTimeout,
                        readTimeout,
                        maxBytes,
                        maxRedirects,
                        Path.of(tempDirectory)));
    }

    public HttpSourceResolver(
            ImportJobRepository jobs,
            ObjectStorage storage,
            SourceResolutionPersistence persistence,
            Clock clock,
            ManagedHttpDownloader downloader) {
        this.jobs = jobs;
        this.storage = storage;
        this.persistence = persistence;
        this.clock = clock;
        this.downloader = downloader;
    }

    @Override
    public boolean supports(ImportJob job) {
        if (job.getSourceType() != ImportSourceType.LINK || job.getSourceUrl() == null) {
            return false;
        }
        try {
            URI uri = URI.create(job.getSourceUrl());
            return "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public boolean resolve(ImportLease lease, ImportLeaseControl control) {
        ImportJob job = jobs.findFencedForUpdate(
                        lease.importId(),
                        lease.owner(),
                        lease.leaseVersion(),
                        lease.inputRevision())
                .orElseThrow(() -> new LeaseLostException(lease));
        if (!control.isValid()) {
            throw new LeaseLostException(lease);
        }
        DownloadedSource downloaded;
        try {
            downloaded = downloader.download(URI.create(job.getSourceUrl()));
        } catch (ImportProcessingException exception) {
            if ("SOURCE_CONTENT_TYPE_UNSUPPORTED".equals(exception.failure().errorCode())
                    && hasUserText(job)) {
                persistence.descriptionOnly(lease, descriptionMetadata(job, sourceUri(job)));
                return true;
            }
            if ("SOURCE_CONTENT_TYPE_UNSUPPORTED".equals(exception.failure().errorCode())) {
                throw new ImportProcessingException(
                        ImportFailure.needsInput("SOURCE_DESCRIPTION_REQUIRED"), exception);
            }
            if ("SOURCE_UNAVAILABLE".equals(exception.failure().errorCode())
                    && hasUserText(job)) {
                persistence.descriptionOnly(lease, unavailableDescriptionMetadata(job));
                return true;
            }
            throw exception;
        }
        try {
            String processingKey = processingKey(lease, downloaded);
            try (var input = Files.newInputStream(downloaded.file())) {
                storage.put(
                        processingKey,
                        input,
                        downloaded.sizeBytes(),
                        downloaded.contentType());
            }
            SourceMetadata metadata = new SourceMetadata(
                    downloaded.canonicalUrl(),
                    null,
                    job.getUserText(),
                    SourceAvailability.AVAILABLE,
                    descriptionAvailability(job));
            persistence.media(
                    lease,
                    processingKey,
                    downloaded.sha256(),
                    downloaded.sizeBytes(),
                    downloaded.contentType(),
                    metadata);
            return true;
        } catch (ImportProcessingException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("SOURCE_STORAGE_PUT_FAILED"), exception);
        } finally {
            delete(downloaded.file());
        }
    }

    private SourceMetadata descriptionMetadata(ImportJob job, URI canonicalUrl) {
        return new SourceMetadata(
                canonicalUrl,
                null,
                job.getUserText(),
                SourceAvailability.PARTIAL,
                descriptionAvailability(job));
    }

    private SourceMetadata unavailableDescriptionMetadata(ImportJob job) {
        return new SourceMetadata(
                sourceUri(job),
                null,
                job.getUserText(),
                SourceAvailability.UNAVAILABLE,
                descriptionAvailability(job));
    }

    private URI sourceUri(ImportJob job) {
        return URI.create(job.getSourceUrl()).normalize();
    }

    private DescriptionAvailability descriptionAvailability(ImportJob job) {
        return hasUserText(job)
                ? DescriptionAvailability.PRESENT
                : DescriptionAvailability.UNAVAILABLE;
    }

    private boolean hasUserText(ImportJob job) {
        return job.getUserText() != null && !job.getUserText().isBlank();
    }

    private String processingKey(ImportLease lease, DownloadedSource source) {
        return "processing/" + lease.userId()
                + "/" + lease.importId()
                + "/http-source/lease-" + lease.leaseVersion()
                + "/" + source.sha256();
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A bounded temporary file can be removed by the next cleanup pass.
        }
    }
}
