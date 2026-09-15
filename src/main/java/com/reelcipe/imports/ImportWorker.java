package com.reelcipe.imports;

import com.reelcipe.imports.domain.ImportFailure;
import com.reelcipe.imports.domain.ImportLease;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Service
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ImportWorker {
    private final ImportQueue queue;
    private final ImportLeasePersistence persistence;
    private final ImportRetryService retryService;
    private final ImportStageHandler handler;
    private final String workerId;
    private final Semaphore slots;
    private final ExecutorService executor;
    private final Map<UUID, RunningLease> running = new ConcurrentHashMap<>();

    public ImportWorker(
            ImportQueue queue,
            ImportLeasePersistence persistence,
            ImportRetryService retryService,
            ObjectProvider<ImportStageHandler> handlers,
            @Value("${app.instance-id}") String workerId,
            @Value("${app.worker.execution-slots:2}") int executionSlots) {
        this.queue = queue;
        this.persistence = persistence;
        this.retryService = retryService;
        this.handler = handlers.getIfAvailable(() -> (lease, control) -> {
        });
        this.workerId = workerId;
        this.slots = new Semaphore(executionSlots);
        this.executor = Executors.newFixedThreadPool(executionSlots);
    }

    @Scheduled(fixedDelayString = "${app.worker.poll-interval-ms:1000}")
    public void poll() {
        while (slots.tryAcquire()) {
            Optional<ImportLease> claim = queue.claimNext(workerId);
            if (claim.isEmpty()) {
                slots.release();
                return;
            }
            submit(claim.get());
        }
    }

    @Scheduled(fixedDelayString = "${app.worker.heartbeat-interval-ms:20000}")
    public void heartbeat() {
        running.values().forEach(runningLease -> {
            if (!persistence.heartbeat(runningLease.lease)) {
                runningLease.control.markLost();
            }
        });
    }

    private void submit(ImportLease lease) {
        RunningLease runningLease = new RunningLease(lease, new ImportLeaseControl());
        running.put(lease.importId(), runningLease);
        try {
            executor.submit(() -> execute(runningLease));
        } catch (RuntimeException exception) {
            running.remove(lease.importId());
            slots.release();
            throw exception;
        }
    }

    private void execute(RunningLease runningLease) {
        try {
            handler.handle(runningLease.lease, runningLease.control);
        } catch (ImportProcessingException exception) {
            retryService.handle(runningLease.lease, exception.failure());
        } catch (RuntimeException exception) {
            retryService.handle(
                    runningLease.lease,
                    ImportFailure.transientError("UNEXPECTED_STAGE_ERROR"));
        } finally {
            running.remove(runningLease.lease.importId());
            slots.release();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private record RunningLease(ImportLease lease, ImportLeaseControl control) {
    }
}
