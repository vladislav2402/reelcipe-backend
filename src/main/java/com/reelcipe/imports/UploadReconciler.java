package com.reelcipe.imports;

import com.reelcipe.imports.domain.UploadAttemptRepository;
import com.reelcipe.imports.domain.UploadAttemptStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class UploadReconciler {
    private final UploadAttemptRepository attempts;
    private final UploadService uploads;
    private final Clock clock;
    private final int batchSize;

    public UploadReconciler(
            UploadAttemptRepository attempts,
            UploadService uploads,
            Clock clock,
            @Value("${app.worker.upload-reconciler-batch-size:20}") int batchSize) {
        this.attempts = attempts;
        this.uploads = uploads;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.worker.upload-reconciler-interval-ms:30000}")
    public void reconcile() {
        reconcileOnce();
    }

    public int reconcileOnce() {
        int completed = 0;
        for (var attempt : attempts.findByStatusAndExpiresAtAfterOrderByCreatedAtAsc(
                UploadAttemptStatus.ACTIVE,
                clock.instant(),
                PageRequest.of(0, batchSize))) {
            try {
                uploads.complete(attempt.getUserId(), attempt.getImportId(), attempt.getId());
                completed++;
            } catch (RuntimeException ignored) {
                // Missing objects and transient storage failures remain eligible for the next pass.
            }
        }
        return completed;
    }
}
