package com.reelcipe.operations.outbox;

import com.reelcipe.operations.outbox.domain.OperationOutbox;
import com.reelcipe.operations.outbox.domain.OutboxLeaseLostException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {
    private final OutboxLeaseService leases;
    private final List<OutboxConsumer> consumers;
    private final String workerId;
    private final int batchSize;

    public OutboxDispatcher(
            OutboxLeaseService leases,
            List<OutboxConsumer> consumers,
            @Qualifier("instanceId") String workerId,
            @Value("${app.worker.operations-batch-size:20}") int batchSize) {
        this.leases = leases;
        this.consumers = consumers;
        this.workerId = workerId;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.worker.operations-interval-ms:60000}")
    public void dispatch() {
        for (int index = 0; index < batchSize; index++) {
            if (!dispatchOnce(workerId + ":outbox")) {
                return;
            }
        }
    }

    public boolean dispatchOnce(String owner) {
        Optional<OutboxLease> claimed = leases.claimNext(owner);
        if (claimed.isEmpty()) {
            return false;
        }
        OutboxLease lease = claimed.get();
        try {
            consumer(lease.event()).consume(lease.event());
            leases.markDelivered(lease);
        } catch (OutboxLeaseLostException ignored) {
            return true;
        } catch (RuntimeException exception) {
            try {
                leases.scheduleRetry(lease, errorCode(exception));
            } catch (OutboxLeaseLostException ignored) {
                // A different dispatcher owns the retry after lease expiry.
            }
        }
        return true;
    }

    private OutboxConsumer consumer(OperationOutbox event) {
        return consumers.stream()
                .filter(candidate -> candidate.supports(event.getEventType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No outbox consumer for " + event.getEventType()));
    }

    private String errorCode(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(message.length(), 1000));
    }
}
