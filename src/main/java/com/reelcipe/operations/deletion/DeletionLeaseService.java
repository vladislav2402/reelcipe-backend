package com.reelcipe.operations.deletion;

import com.reelcipe.operations.OperationBackoff;
import com.reelcipe.operations.deletion.domain.DeletionLeaseLostException;
import com.reelcipe.operations.deletion.domain.DeletionTask;
import com.reelcipe.operations.deletion.domain.DeletionTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class DeletionLeaseService {
    private final DeletionTaskRepository repository;
    private final OperationBackoff backoff;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration clockSkewTolerance;
    private final int maxAttempts;

    public DeletionLeaseService(
            DeletionTaskRepository repository,
            OperationBackoff backoff,
            Clock clock,
            @Value("${app.worker.operations-lease-duration:PT90S}") Duration leaseDuration,
            @Value("${app.worker.clock-skew-tolerance:PT1S}") Duration clockSkewTolerance,
            @Value("${app.worker.operations-max-attempts:8}") int maxAttempts) {
        this.repository = repository;
        this.backoff = backoff;
        this.clock = clock;
        this.leaseDuration = leaseDuration;
        this.clockSkewTolerance = clockSkewTolerance;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public Optional<DeletionLease> claimNext(String owner) {
        Optional<DeletionTask> candidate = repository.findNextClaimable(
                clockSkewTolerance.toMillis() + " milliseconds");
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        DeletionTask task = candidate.get();
        task.claim(owner, clock.instant().plus(leaseDuration), clock);
        repository.save(task);
        return Optional.of(new DeletionLease(
                task.getId(), owner, task.getLeaseVersion(), task));
    }

    @Transactional
    public void markCompleted(DeletionLease lease) {
        DeletionTask task = repository.findLockedByLease(
                        lease.id(), lease.owner(), lease.version())
                .orElseThrow(() -> new DeletionLeaseLostException(lease.id()));
        task.markCompleted(lease.owner(), lease.version(), clock);
        repository.save(task);
    }

    @Transactional
    public void scheduleRetry(DeletionLease lease, String error) {
        DeletionTask task = repository.findLockedByLease(
                        lease.id(), lease.owner(), lease.version())
                .orElseThrow(() -> new DeletionLeaseLostException(lease.id()));
        Instant nextAttempt = backoff.nextAttemptAt(clock.instant(), task.getAttempts() + 1);
        task.scheduleRetry(
                lease.owner(),
                lease.version(),
                nextAttempt,
                error,
                maxAttempts,
                clock);
        repository.save(task);
    }
}
