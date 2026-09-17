package com.reelcipe.imports;

import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.billing.QuotaService;
import com.reelcipe.imports.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ImportRetryService {
    private final ImportJobRepository jobs;
    private final UserRepository users;
    private final QuotaService quota;
    private final RetryPolicy retryPolicy;
    private final Clock clock;
    private final int maxStageAttempts;
    private final Duration inputWaitDuration;

    public ImportRetryService(
            ImportJobRepository jobs,
            UserRepository users,
            QuotaService quota,
            RetryPolicy retryPolicy,
            Clock clock,
            @Value("${app.worker.max-stage-attempts:3}") int maxStageAttempts,
            @Value("${app.worker.input-wait-duration:PT24H}") Duration inputWaitDuration) {
        this.jobs = jobs;
        this.users = users;
        this.quota = quota;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
        this.maxStageAttempts = maxStageAttempts;
        this.inputWaitDuration = inputWaitDuration;
    }

    @Transactional
    public FailureOutcome handle(ImportLease lease, ImportFailure failure) {
        return handle(lease, failure, null);
    }

    @Transactional
    public FailureOutcome handle(
            ImportLease lease,
            ImportFailure failure,
            java.time.Instant retryAfter) {
        if (users.findLockedByIdAndStatus(lease.userId(), UserStatus.ACTIVE).isEmpty()) {
            return FailureOutcome.LEASE_LOST;
        }
        ImportJob job = jobs.findFencedForUpdate(lease.importId(), lease.owner(), lease.leaseVersion(),
                lease.inputRevision()).orElse(null);
        if (job == null) {
            return FailureOutcome.LEASE_LOST;
        }
        if (failure.kind() == ImportFailureKind.NEEDS_INPUT) {
            job.awaitInput(failure.errorCode(), inputDeadline(job), clock);
            jobs.save(job);
            return FailureOutcome.NEEDS_INPUT;
        }
        if (failure.kind() == ImportFailureKind.PERMANENT || job.getStageAttempts() + 1 >= maxStageAttempts) {
            job.fail(failure.errorCode(), clock);
            jobs.save(job);
            quota.releaseImport(job.getUserId(), job.getId());
            return FailureOutcome.FAILED;
        }
        java.time.Instant nextAttempt = retryPolicy.nextAttemptAt(
                clock.instant(), job.getStageAttempts() + 1);
        if (retryAfter != null && retryAfter.isAfter(nextAttempt)) {
            nextAttempt = retryAfter;
        }
        job.scheduleRetry(
                nextAttempt,
                failure.errorCode(),
                clock);
        jobs.save(job);
        return FailureOutcome.RETRY_WAIT;
    }

    private java.time.Instant inputDeadline(ImportJob job) {
        java.time.Instant candidate = clock.instant().plus(inputWaitDuration);
        return candidate.isBefore(job.getProcessingDeadlineAt())
                ? candidate
                : job.getProcessingDeadlineAt();
    }

    public enum FailureOutcome {
        RETRY_WAIT,
        NEEDS_INPUT,
        FAILED,
        LEASE_LOST
    }
}
