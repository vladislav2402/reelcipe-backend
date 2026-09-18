package com.reelcipe.imports.source.tiktok;

import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.*;
import com.reelcipe.imports.source.*;
import com.reelcipe.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class TikTokSourceResolver implements SourceResolver {
    private final ImportJobRepository jobs;
    private final ObjectStorage storage;
    private final SourceResolutionPersistence persistence;
    private final ManagedHttpDownloader downloader;
    private final TikTokResolverProvider provider;
    private final boolean enabled;

    @Autowired
    public TikTokSourceResolver(
            ImportJobRepository jobs,
            ObjectStorage storage,
            SourceResolutionPersistence persistence,
            ManagedHttpDownloader downloader,
            List<TikTokResolverProvider> providers,
            @Value("${app.features.enabled-link-platforms:}") String enabledPlatforms,
            @Value("${app.import.tiktok.provider:rapidapi}") String providerName) {
        this.jobs = jobs;
        this.storage = storage;
        this.persistence = persistence;
        this.downloader = downloader;
        this.provider = providers.stream()
                .filter(candidate -> providerName.equalsIgnoreCase(candidate.providerName()))
                .findFirst()
                .orElse(null);
        this.enabled = hasPlatform(enabledPlatforms, "tiktok")
                && provider != null
                && provider.configured();
    }

    TikTokSourceResolver(
            ImportJobRepository jobs,
            ObjectStorage storage,
            SourceResolutionPersistence persistence,
            ManagedHttpDownloader downloader,
            TikTokResolverProvider provider,
            boolean enabled) {
        this.jobs = jobs;
        this.storage = storage;
        this.persistence = persistence;
        this.downloader = downloader;
        this.provider = provider;
        this.enabled = enabled;
    }

    @Override
    public boolean supports(ImportJob job) {
        return enabled
                && job.getSourceType() == ImportSourceType.LINK
                && isTikTokUrl(job.getSourceUrl());
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
        TikTokResolverProvider.Resolution result = provider.resolve(URI.create(job.getSourceUrl()));
        URI canonicalUrl = canonicalUrl(result, job);
        DescriptionAvailability description = descriptionAvailability(result);
        URI mediaUrl = mediaUrl(result);
        if (mediaUrl == null) {
            if (description == DescriptionAvailability.PRESENT) {
                persistence.descriptionOnly(
                        lease,
                        metadata(
                                result,
                                canonicalUrl,
                                job.getUserText(),
                                description,
                                audioAvailability(result)));
                return true;
            }
            throw new ImportProcessingException(
                    ImportFailure.needsInput("SOURCE_MEDIA_OR_DESCRIPTION_REQUIRED"));
        }
        if (!control.isValid()) {
            throw new LeaseLostException(lease);
        }
        DownloadedSource downloaded = downloader.download(mediaUrl);
        Path file = downloaded.file();
        try {
            String key = processingKey(lease, downloaded);
            try (var input = Files.newInputStream(file)) {
                storage.put(key, input, downloaded.sizeBytes(), downloaded.contentType());
            }
            persistence.media(
                    lease,
                    key,
                    downloaded.sha256(),
                    downloaded.sizeBytes(),
                    downloaded.contentType(),
                    metadata(
                            result,
                            canonicalUrl,
                            job.getUserText(),
                            description,
                            AudioAvailability.AVAILABLE));
            return true;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ImportProcessingException processingException) {
                throw processingException;
            }
            throw new ImportProcessingException(
                    ImportFailure.transientError("SOURCE_STORAGE_PUT_FAILED"), exception);
        } finally {
            delete(file);
        }
    }

    private URI mediaUrl(TikTokResolverProvider.Resolution result) {
        if ("AVAILABLE".equalsIgnoreCase(result.audioStatus())
                && result.videoUrl() != null) {
            return result.videoUrl();
        }
        if ("AVAILABLE".equalsIgnoreCase(result.audioStatus())
                && "FULL_POST_AUDIO".equalsIgnoreCase(result.mediaKind())) {
            return result.audioUrl();
        }
        return null;
    }

    private SourceMetadata metadata(
            TikTokResolverProvider.Resolution result,
            URI canonicalUrl,
            String userText,
            DescriptionAvailability description,
            AudioAvailability audio) {
        return new SourceMetadata(
                canonicalUrl,
                "TIKTOK",
                result.authorName(),
                result.authorUrl() == null ? null : result.authorUrl().toString(),
                result.authorDescription(),
                userText,
                SourceAvailability.AVAILABLE,
                description,
                audio,
                result.transcript(),
                result.transcriptLanguage(),
                provider.providerName());
    }

    private DescriptionAvailability descriptionAvailability(
            TikTokResolverProvider.Resolution result) {
        if ("AVAILABLE".equalsIgnoreCase(result.descriptionStatus())
                || (result.authorDescription() != null && !result.authorDescription().isBlank())) {
            return DescriptionAvailability.PRESENT;
        }
        if ("EMPTY".equalsIgnoreCase(result.descriptionStatus())) {
            return DescriptionAvailability.EMPTY;
        }
        return DescriptionAvailability.UNAVAILABLE;
    }

    private AudioAvailability audioAvailability(TikTokResolverProvider.Resolution result) {
        if (mediaUrl(result) != null) {
            return AudioAvailability.AVAILABLE;
        }
        if ("MISSING".equalsIgnoreCase(result.audioStatus())
                || "MUSIC_ONLY".equalsIgnoreCase(result.mediaKind())) {
            return AudioAvailability.MISSING;
        }
        return AudioAvailability.UNAVAILABLE;
    }

    private boolean isTikTokUrl(String sourceUrl) {
        if (sourceUrl == null) {
            return false;
        }
        try {
            URI uri = URI.create(sourceUrl);
            String host = uri.getHost();
            if (host == null || !"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            return normalized.equals("tiktok.com")
                    || normalized.endsWith(".tiktok.com");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasPlatform(String value, String platform) {
        if (value == null) {
            return false;
        }
        for (String item : value.split(",")) {
            if (platform.equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    private URI canonicalUrl(
            TikTokResolverProvider.Resolution result,
            ImportJob job) {
        URI fallback = URI.create(job.getSourceUrl()).normalize();
        URI candidate = result.canonicalUrl();
        if (candidate == null || !"https".equalsIgnoreCase(candidate.getScheme())
                || candidate.getHost() == null
                || !isTikTokHost(candidate.getHost())) {
            return fallback;
        }
        return candidate.normalize();
    }

    private boolean isTikTokHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("tiktok.com") || normalized.endsWith(".tiktok.com");
    }

    private String processingKey(ImportLease lease, DownloadedSource source) {
        String variant = source.variant() == SourceVariant.FULL_AUDIO ? "audio" : "video";
        return "processing/" + lease.userId()
                + "/" + lease.importId()
                + "/tiktok-" + variant + "/lease-" + lease.leaseVersion()
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
