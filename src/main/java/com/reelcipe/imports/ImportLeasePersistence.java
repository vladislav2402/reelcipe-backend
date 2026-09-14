package com.reelcipe.imports;

import com.reelcipe.imports.domain.ImportJob;
import com.reelcipe.imports.domain.ImportJobRepository;
import com.reelcipe.imports.domain.ImportLease;
import com.reelcipe.imports.domain.LeaseLostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ImportLeasePersistence {
    private final ImportJobRepository jobs;
    private final Duration leaseDuration;

    public ImportLeasePersistence(
            ImportJobRepository jobs,
            @Value("${app.worker.lease-duration:PT90S}") Duration leaseDuration) {
        this.jobs = jobs;
        this.leaseDuration = leaseDuration;
    }

    @Transactional
    public boolean heartbeat(ImportLease lease) {
        return jobs.heartbeat(
                lease.importId(),
                lease.owner(),
                lease.leaseVersion(),
                lease.inputRevision(),
                postgresInterval(leaseDuration)) == 1;
    }

    @Transactional
    public void checkpoint(ImportLease lease, String reference) {
        ImportJob job = jobs.findFencedForUpdate(
                        lease.importId(),
                        lease.owner(),
                        lease.leaseVersion(),
                        lease.inputRevision())
                .orElseThrow(() -> new LeaseLostException(lease));
        job.checkpoint(lease.stage(), reference, java.time.Clock.systemUTC());
        jobs.save(job);
    }

    private String postgresInterval(Duration duration) {
        return duration.toMillis() + " milliseconds";
    }
}
