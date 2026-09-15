package com.reelcipe.imports;

import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.billing.QuotaService;
import com.reelcipe.imports.domain.ImportJob;
import com.reelcipe.imports.domain.ImportJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ImportExpiryService {
    private final ImportJobRepository jobs;
    private final UserRepository users;
    private final QuotaService quota;
    private final Clock clock;
    private final int batchSize;

    public ImportExpiryService(
            ImportJobRepository jobs,
            UserRepository users,
            QuotaService quota,
            Clock clock,
            @Value("${app.worker.expiry-batch-size:20}") int batchSize) {
        this.jobs = jobs;
        this.users = users;
        this.quota = quota;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.worker.expiry-interval-ms:60000}")
    @Transactional
    public void expireDue() {
        for (int i = 0; i < batchSize; i++) {
            if (!expireOne()) {
                return;
            }
        }
    }

    private boolean expireOne() {
        UUID jobId = jobs.findNextExpiredId().orElse(null);
        if (jobId == null) {
            return false;
        }
        ImportJob candidate = jobs.findById(jobId).orElse(null);
        if (candidate == null) {
            return true;
        }
        if (users.findLockedByIdAndStatus(candidate.getUserId(), UserStatus.ACTIVE).isEmpty()) {
            return true;
        }
        ImportJob job = jobs.findLockedByIdAndUserId(jobId, candidate.getUserId()).orElse(null);
        if (job == null || !job.isExpiredAt(clock.instant())) {
            return true;
        }
        job.expire(clock);
        jobs.save(job);
        quota.releaseImport(job.getUserId(), job.getId());
        return true;
    }
}
