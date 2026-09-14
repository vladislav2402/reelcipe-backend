package com.reelcipe.imports;

import com.reelcipe.imports.domain.ImportJob;
import com.reelcipe.imports.domain.ImportJobRepository;
import com.reelcipe.imports.domain.ImportLease;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ImportQueue {
    private final ImportJobRepository jobs;
    private final Clock clock;
    private final Duration leaseDuration;

    public ImportQueue(
            ImportJobRepository jobs,
            Clock clock,
            @Value("${app.worker.lease-duration:PT90S}") Duration leaseDuration) {
        this.jobs = jobs;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
    }

    @Transactional
    public Optional<ImportLease> claimNext(String workerId) {
        Optional<ImportJob> candidate = jobs.findNextClaimable();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        ImportJob job = candidate.get();
        job.claimForProcessing(workerId, clock.instant().plus(leaseDuration), clock);
        jobs.save(job);
        return Optional.of(ImportLease.from(job));
    }
}
