package com.reelcipe.imports;

import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.billing.QuotaService;
import com.reelcipe.imports.domain.ImportJob;
import com.reelcipe.imports.domain.ImportJobRepository;
import com.reelcipe.imports.domain.ImportStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ImportLifecycleService {
    private final ImportJobRepository jobs;
    private final UserRepository users;
    private final QuotaService quota;
    private final ImportService imports;
    private final Clock clock;
    private final Duration inputWaitDuration;
    private final int maxInputContinuations;
    private final int maxDescriptionCharacters;

    public ImportLifecycleService(
            ImportJobRepository jobs,
            UserRepository users,
            QuotaService quota,
            ImportService imports,
            Clock clock,
            @Value("${app.worker.input-wait-duration:PT24H}") Duration inputWaitDuration,
            @Value("${app.worker.max-input-continuations:3}") int maxInputContinuations,
            @Value("${app.limits.max-description-characters:20000}") int maxDescriptionCharacters) {
        this.jobs = jobs;
        this.users = users;
        this.quota = quota;
        this.imports = imports;
        this.clock = clock;
        this.inputWaitDuration = inputWaitDuration;
        this.maxInputContinuations = maxInputContinuations;
        this.maxDescriptionCharacters = maxDescriptionCharacters;
    }

    @Transactional
    public ImportService.ImportView cancel(UUID userId, UUID importId) {
        lockUser(userId);
        ImportJob job = ownedJob(userId, importId);
        if (!isTerminal(job.getStatus())) {
            job.transitionTo(ImportStatus.CANCELLED, clock);
            jobs.save(job);
            quota.releaseImport(userId, importId);
        }
        return view(job, userId);
    }

    @Transactional
    public ImportService.ImportView retry(UUID userId, UUID importId) {
        lockUser(userId);
        ImportJob job = ownedJob(userId, importId);
        if (job.getStatus() != ImportStatus.FAILED) {
            throw conflict("Only failed imports can be retried");
        }
        if (job.getErrorCode() != null && job.getErrorCode().startsWith("PERMANENT_")) {
            throw conflict("This import failure is not retryable");
        }
        quota.reserveNextGeneration(userId, importId);
        job.retry(clock);
        jobs.save(job);
        return view(job, userId);
    }

    @Transactional
    public ImportService.ImportView continueWithDescription(UUID userId, UUID importId, String descriptionText) {
        lockUser(userId);
        if (descriptionText == null || descriptionText.isBlank()
                || descriptionText.length() > maxDescriptionCharacters) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is invalid");
        }
        ImportJob job = ownedJob(userId, importId);
        if (job.getStatus() != ImportStatus.NEEDS_INPUT) {
            throw conflict("Import does not wait for input");
        }
        Instant deadline = clock.instant().plus(inputWaitDuration);
        if (deadline.isAfter(job.getProcessingDeadlineAt())) {
            deadline = job.getProcessingDeadlineAt();
        }
        job.changeInputRevision(
                sha256(descriptionText),
                descriptionText,
                false,
                deadline,
                maxInputContinuations,
                clock);
        jobs.save(job);
        return view(job, userId);
    }

    private void lockUser(UUID userId) {
        if (userId == null || users.findLockedByIdAndStatus(userId, UserStatus.ACTIVE).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not active");
        }
    }

    private ImportJob ownedJob(UUID userId, UUID importId) {
        return jobs.findLockedByIdAndUserId(importId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found"));
    }

    private ImportService.ImportView view(ImportJob job, UUID userId) {
        return imports.view(job, quota.current(userId));
    }

    private boolean isTerminal(ImportStatus status) {
        return status == ImportStatus.READY || status == ImportStatus.REVIEW_REQUIRED
                || status == ImportStatus.FAILED || status == ImportStatus.CANCELLED
                || status == ImportStatus.EXPIRED;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
