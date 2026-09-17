package com.reelcipe.imports.source;

import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class SourceResolverRegistry {
    private final ImportJobRepository jobs;
    private final SourceResolutionPersistence persistence;
    private final List<SourceResolver> resolvers;
    private final Set<String> enabledPlatforms;

    public SourceResolverRegistry(
            ImportJobRepository jobs,
            SourceResolutionPersistence persistence,
            List<SourceResolver> resolvers,
            @Value("${app.features.enabled-link-platforms:}") String enabledPlatforms) {
        this.jobs = jobs;
        this.persistence = persistence;
        this.resolvers = List.copyOf(resolvers);
        this.enabledPlatforms = Arrays.stream(enabledPlatforms.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean resolve(ImportLease lease, ImportLeaseControl control) {
        ImportJob job = jobs.findFencedForUpdate(
                        lease.importId(),
                        lease.owner(),
                        lease.leaseVersion(),
                        lease.inputRevision())
                .orElseThrow(() -> new IllegalStateException("Import lease was not found"));
        if (job.getSourceType() != ImportSourceType.LINK) {
            return false;
        }
        for (SourceResolver resolver : resolvers) {
            if (resolver.supports(job)) {
                resolver.resolve(lease, control);
                return true;
            }
        }
        if (job.getUserText() != null && !job.getUserText().isBlank()) {
            persistence.descriptionOnly(
                    lease,
                    new SourceMetadata(
                            canonicalUrl(job),
                            null,
                            job.getUserText(),
                            SourceAvailability.UNAVAILABLE,
                            DescriptionAvailability.PRESENT));
            return true;
        }
        throw new ImportProcessingException(ImportFailure.needsInput("SOURCE_PLATFORM_UNSUPPORTED"));
    }

    public Set<String> enabledPlatforms() {
        return enabledPlatforms;
    }

    public SourceResolutionPersistence persistence() {
        return persistence;
    }

    private java.net.URI canonicalUrl(ImportJob job) {
        try {
            return java.net.URI.create(job.getSourceUrl()).normalize();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
