package com.reelcipe.imports;

import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.imports.domain.*;
import com.reelcipe.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class UploadService {
    private final UploadAttemptRepository attempts;
    private final MediaAssetRepository assets;
    private final ImportJobRepository jobs;
    private final UserRepository users;
    private final ObjectStorage storage;
    private final Clock clock;
    private final Duration uploadUrlTtl;
    private final int maxAttempts;
    private UploadService transactionalProxy;

    public UploadService(
            UploadAttemptRepository attempts,
            MediaAssetRepository assets,
            ImportJobRepository jobs,
            UserRepository users,
            ObjectStorage storage,
            Clock clock,
            @Value("${app.import.upload-url-ttl:PT1H}") Duration uploadUrlTtl,
            @Value("${app.import.max-upload-attempts:3}") int maxAttempts) {
        this.attempts = attempts;
        this.assets = assets;
        this.jobs = jobs;
        this.users = users;
        this.storage = storage;
        this.clock = clock;
        this.uploadUrlTtl = uploadUrlTtl;
        this.maxAttempts = maxAttempts;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setTransactionalProxy(@Lazy UploadService transactionalProxy) {
        this.transactionalProxy = transactionalProxy;
    }

    @Transactional
    UploadAttempt createInitialAttempt(ImportJob job) {
        if (job.getSourceType() != ImportSourceType.UPLOAD) {
            throw new IllegalArgumentException("Upload attempt requires an upload import");
        }
        Instant now = clock.instant();
        Instant urlExpiresAt = urlExpiresAt(now, job.getInputDeadlineAt());
        UUID attemptId = UUID.randomUUID();
        UploadAttempt attempt = new UploadAttempt(
                attemptId,
                job.getId(),
                job.getUserId(),
                1,
                stagingKey(job.getUserId(), job.getId(), attemptId),
                job.getInputDeadlineAt(),
                urlExpiresAt,
                job.getExpectedSizeBytes(),
                job.getContentType(),
                now);
        return attempts.save(attempt);
    }

    public UploadUrlResponse getUploadUrl(UUID userId, UUID importId) {
        UploadGrant grant = transactionalService().prepareUpload(userId, importId);
        Duration duration = Duration.between(clock.instant(), grant.urlExpiresAt());
        ObjectStorage.PresignedUpload signed = storage.presignPut(
                grant.stagingKey(), grant.contentType(), duration);
        return new UploadUrlResponse(
                importId,
                grant.attemptId(),
                signed.url(),
                grant.urlExpiresAt(),
                grant.expectedSizeBytes(),
                grant.contentType());
    }

    public UploadCompletionResponse complete(
            UUID userId,
            UUID importId,
            UUID attemptId) {
        CompletionTarget target = transactionalService()
                .prepareCompletion(userId, importId, attemptId);
        if (target.alreadyCompleted()) {
            return target.response();
        }
        ObjectStorage.ObjectMetadata metadata = storage.head(target.stagingKey())
                .orElseThrow(() -> conflict("Uploaded object was not found"));
        if (metadata.sizeBytes() != target.expectedSizeBytes()) {
            throw conflict("Uploaded object size does not match the declared size");
        }
        if (metadata.contentType() != null
                && !target.contentType().equalsIgnoreCase(metadata.contentType())) {
            throw conflict("Uploaded object content type does not match the declared type");
        }
        return transactionalService().commitCompletion(target, metadata);
    }

    public UploadCompletionResponse attachUpload(
            UUID userId,
            UUID importId,
            UUID attemptId) {
        return complete(userId, importId, attemptId);
    }

    @Transactional
    public UploadGrant prepareUpload(UUID userId, UUID importId) {
        lockActiveUser(userId);
        ImportJob job = ownedJob(userId, importId);
        if (job.getSourceType() != ImportSourceType.UPLOAD) {
            throw conflict("Import is not an upload");
        }
        if (job.getStatus() != ImportStatus.AWAITING_UPLOAD) {
            throw conflict("Upload is no longer expected for this import");
        }
        UploadAttempt latest = latestAttempt(importId, userId).orElse(null);
        Instant now = clock.instant();
        if (latest != null && latest.hasActiveUrlAt(now)) {
            return grant(latest);
        }
        if (latest != null && latest.getStatus() == UploadAttemptStatus.ACTIVE) {
            latest.supersede(clock);
            attempts.save(latest);
        }
        int nextNumber = latest == null ? 1 : latest.getAttemptNumber() + 1;
        if (nextNumber > maxAttempts) {
            throw conflict("Maximum upload attempts reached");
        }
        Instant urlExpiresAt = urlExpiresAt(now, job.getInputDeadlineAt());
        UUID attemptId = UUID.randomUUID();
        UploadAttempt attempt = new UploadAttempt(
                attemptId,
                job.getId(),
                userId,
                nextNumber,
                stagingKey(userId, importId, attemptId),
                job.getInputDeadlineAt(),
                urlExpiresAt,
                job.getExpectedSizeBytes(),
                job.getContentType(),
                now);
        return grant(attempts.save(attempt));
    }

    @Transactional
    public CompletionTarget prepareCompletion(UUID userId, UUID importId, UUID attemptId) {
        lockActiveUser(userId);
        ImportJob job = ownedJob(userId, importId);
        UploadAttempt latest = latestAttempt(importId, userId)
                .orElseThrow(() -> conflict("Upload attempt was not found"));
        if (!latest.getId().equals(attemptId)) {
            throw conflict("Upload attempt is no longer current");
        }
        Optional<MediaAsset> existing = assets.findByUploadAttemptId(attemptId);
        if (existing.isPresent()) {
            return CompletionTarget.completed(completion(existing.get(), job));
        }
        if (!latest.isActiveAt(clock.instant())
                || job.getStatus() != ImportStatus.AWAITING_UPLOAD) {
            throw conflict("Upload attempt is no longer active");
        }
        return new CompletionTarget(
                userId,
                importId,
                attemptId,
                latest.getStagingKey(),
                latest.getExpectedSizeBytes(),
                latest.getExpectedContentType(),
                job.getProcessingDeadlineAt(),
                false,
                null);
    }

    @Transactional
    public UploadCompletionResponse commitCompletion(
            CompletionTarget target,
            ObjectStorage.ObjectMetadata metadata) {
        lockActiveUser(target.userId());
        ImportJob job = ownedJob(target.userId(), target.importId());
        UploadAttempt latest = latestAttempt(target.importId(), target.userId())
                .orElseThrow(() -> conflict("Upload attempt was not found"));
        Optional<MediaAsset> existing = assets.findByUploadAttemptId(target.attemptId());
        if (existing.isPresent()) {
            return completion(existing.get(), job);
        }
        if (!latest.getId().equals(target.attemptId())
                || !latest.isActiveAt(clock.instant())
                || job.getStatus() != ImportStatus.AWAITING_UPLOAD) {
            throw conflict("Upload attempt is no longer current");
        }
        MediaAsset asset = new MediaAsset(
                UUID.randomUUID(),
                target.importId(),
                target.userId(),
                target.attemptId(),
                MediaAssetType.SOURCE,
                target.stagingKey(),
                metadata.sizeBytes(),
                target.contentType(),
                target.processingDeadlineAt(),
                clock.instant());
        latest.complete(clock);
        attempts.save(latest);
        assets.save(asset);
        job.transitionTo(ImportStatus.QUEUED, clock);
        jobs.save(job);
        return completion(asset, job);
    }

    private UploadCompletionResponse completion(MediaAsset asset, ImportJob job) {
        return new UploadCompletionResponse(
                job.getId(),
                asset.getUploadAttemptId(),
                asset.getId(),
                job.getStatus());
    }

    private UploadGrant grant(UploadAttempt attempt) {
        return new UploadGrant(
                attempt.getId(),
                attempt.getStagingKey(),
                attempt.getUrlExpiresAt(),
                attempt.getExpectedSizeBytes(),
                attempt.getExpectedContentType());
    }

    private Optional<UploadAttempt> latestAttempt(UUID importId, UUID userId) {
        return attempts.findLatestLocked(importId, userId, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    private ImportJob ownedJob(UUID userId, UUID importId) {
        return jobs.findLockedByIdAndUserId(importId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found"));
    }

    private void lockActiveUser(UUID userId) {
        if (users.findLockedByIdAndStatus(userId, UserStatus.ACTIVE).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not active");
        }
    }

    private Instant urlExpiresAt(Instant now, Instant uploadDeadline) {
        Instant candidate = now.plus(uploadUrlTtl);
        return candidate.isBefore(uploadDeadline) ? candidate : uploadDeadline;
    }

    private String stagingKey(UUID userId, UUID importId, UUID attemptId) {
        return "staging/" + userId + "/" + importId + "/" + attemptId + "/source";
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private UploadService transactionalService() {
        return transactionalProxy == null ? this : transactionalProxy;
    }

    public record UploadUrlResponse(
            UUID importId,
            UUID uploadAttemptId,
            String url,
            Instant expiresAt,
            long expectedSizeBytes,
            String contentType) {
    }

    public record UploadCompletionResponse(
            UUID importId,
            UUID uploadAttemptId,
            UUID mediaAssetId,
            ImportStatus status) {
    }

    record UploadGrant(
            UUID attemptId,
            String stagingKey,
            Instant urlExpiresAt,
            long expectedSizeBytes,
            String contentType) {
    }

    record CompletionTarget(
            UUID userId,
            UUID importId,
            UUID attemptId,
            String stagingKey,
            long expectedSizeBytes,
            String contentType,
            Instant processingDeadlineAt,
            boolean alreadyCompleted,
            UploadCompletionResponse response) {
        static CompletionTarget completed(UploadCompletionResponse response) {
            return new CompletionTarget(
                    null, null, null, null, 0, null, null, true, response);
        }
    }
}
