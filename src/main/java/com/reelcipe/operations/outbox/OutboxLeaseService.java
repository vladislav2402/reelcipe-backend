package com.reelcipe.operations.outbox;

import com.reelcipe.operations.OperationBackoff;
import com.reelcipe.operations.outbox.domain.OperationOutbox;
import com.reelcipe.operations.outbox.domain.OperationOutboxRepository;
import com.reelcipe.operations.outbox.domain.OutboxLeaseLostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class OutboxLeaseService {
    private final OperationOutboxRepository repository;
    private final OperationBackoff backoff;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration clockSkewTolerance;
    private final int maxAttempts;

    public OutboxLeaseService(
            OperationOutboxRepository repository,
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
    public Optional<OutboxLease> claimNext(String owner) {
        Optional<OperationOutbox> candidate = repository.findNextClaimable(
                clockSkewTolerance.toMillis() + " milliseconds");
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        OperationOutbox event = candidate.get();
        event.claim(owner, clock.instant().plus(leaseDuration), clock);
        repository.save(event);
        return Optional.of(new OutboxLease(
                event.getId(), owner, event.getLeaseVersion(), event));
    }

    @Transactional
    public void markDelivered(OutboxLease lease) {
        OperationOutbox event = repository.findLockedByLease(
                        lease.id(), lease.owner(), lease.version())
                .orElseThrow(() -> new OutboxLeaseLostException(lease.id()));
        event.markDelivered(lease.owner(), lease.version(), clock);
        repository.save(event);
    }

    @Transactional
    public void scheduleRetry(OutboxLease lease, String error) {
        OperationOutbox event = repository.findLockedByLease(
                        lease.id(), lease.owner(), lease.version())
                .orElseThrow(() -> new OutboxLeaseLostException(lease.id()));
        Instant nextAttempt = backoff.nextAttemptAt(clock.instant(), event.getAttempts() + 1);
        event.scheduleRetry(
                lease.owner(),
                lease.version(),
                nextAttempt,
                error,
                maxAttempts,
                clock);
        repository.save(event);
    }
}
