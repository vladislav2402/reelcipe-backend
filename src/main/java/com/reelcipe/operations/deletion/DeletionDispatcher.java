package com.reelcipe.operations.deletion;

import com.reelcipe.operations.deletion.domain.DeletionLeaseLostException;
import com.reelcipe.operations.deletion.domain.DeletionTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class DeletionDispatcher {
    private final DeletionLeaseService leases;
    private final List<DeletionHandler> handlers;
    private final String workerId;
    private final int batchSize;

    public DeletionDispatcher(
            DeletionLeaseService leases,
            List<DeletionHandler> handlers,
            @Value("${app.instance-id}") String workerId,
            @Value("${app.worker.operations-batch-size:20}") int batchSize) {
        this.leases = leases;
        this.handlers = handlers;
        this.workerId = workerId;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.worker.operations-interval-ms:60000}")
    public void dispatch() {
        for (int index = 0; index < batchSize; index++) {
            if (!dispatchOnce(workerId + ":deletion")) {
                return;
            }
        }
    }

    public boolean dispatchOnce(String owner) {
        Optional<DeletionLease> claimed = leases.claimNext(owner);
        if (claimed.isEmpty()) {
            return false;
        }
        DeletionLease lease = claimed.get();
        try {
            handler(lease.task()).delete(lease.task());
            leases.markCompleted(lease);
        } catch (DeletionLeaseLostException ignored) {
            return true;
        } catch (RuntimeException exception) {
            try {
                leases.scheduleRetry(lease, errorCode(exception));
            } catch (DeletionLeaseLostException ignored) {
                // A different dispatcher owns the retry after lease expiry.
            }
        }
        return true;
    }

    private DeletionHandler handler(DeletionTask task) {
        return handlers.stream()
                .filter(candidate -> candidate.supports(task.getResourceType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No deletion handler for " + task.getResourceType()));
    }

    private String errorCode(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 1000));
    }
}
