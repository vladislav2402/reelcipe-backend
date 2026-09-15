package com.reelcipe.imports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.billing.QuotaService;
import com.reelcipe.idempotency.IdempotencyService;
import com.reelcipe.idempotency.domain.IdempotencyResult;
import com.reelcipe.imports.domain.*;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ImportService {
    private static final Set<ImportStatus> ACTIVE_STATUSES = Set.of(
            ImportStatus.AWAITING_UPLOAD, ImportStatus.QUEUED, ImportStatus.RESOLVING,
            ImportStatus.EXTRACTING_AUDIO, ImportStatus.TRANSCRIBING,
            ImportStatus.EXTRACTING_RECIPE, ImportStatus.VALIDATING,
            ImportStatus.RETRY_WAIT, ImportStatus.NEEDS_INPUT);

    private final ImportJobRepository jobs;
    private final UserRepository users;
    private final IdempotencyService idempotency;
    private final QuotaService quota;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int maxActiveImports;
    private final int maxDescriptionCharacters;
    private final long maxVideoSizeBytes;
    private final long maxAudioSizeBytes;
    private final Duration uploadTtl;
    private final Duration processingTtl;

    public ImportService(
            ImportJobRepository jobs,
            UserRepository users,
            IdempotencyService idempotency,
            QuotaService quota,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.limits.max-active-imports-per-user:2}") int maxActiveImports,
            @Value("${app.limits.max-description-characters:20000}") int maxDescriptionCharacters,
            @Value("${app.limits.max-video-size-bytes:104857600}") long maxVideoSizeBytes,
            @Value("${app.limits.max-audio-size-bytes:20971520}") long maxAudioSizeBytes,
            @Value("${app.import.upload-ttl:PT24H}") Duration uploadTtl,
            @Value("${app.import.processing-ttl:PT48H}") Duration processingTtl) {
        this.jobs = jobs;
        this.users = users;
        this.idempotency = idempotency;
        this.quota = quota;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.maxActiveImports = maxActiveImports;
        this.maxDescriptionCharacters = maxDescriptionCharacters;
        this.maxVideoSizeBytes = maxVideoSizeBytes;
        this.maxAudioSizeBytes = maxAudioSizeBytes;
        this.uploadTtl = uploadTtl;
        this.processingTtl = processingTtl;
    }

    @Transactional
    public ImportView create(UUID userId, String idempotencyKey, ImportCommand command) {
        validate(userId, command);
        users.findLockedByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not active"));
        String body = json(command);
        IdempotencyResult result = idempotency.execute(
                userId,
                "import.create",
                "imports",
                idempotencyKey,
                body,
                () -> IdempotencyResult.accepted(json(createNew(userId, command, body))));
        return readJson(result.body(), ImportView.class);
    }

    @Transactional
    public List<ImportView> list(UUID userId) {
        QuotaService.QuotaSnapshot quotaSnapshot = quota.current(userId);
        return jobs.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(job -> view(job, quotaSnapshot))
                .toList();
    }

    @Transactional
    public ImportView get(UUID userId, UUID importId) {
        ImportJob job = jobs.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found"));
        return view(job, quota.current(userId));
    }

    private ImportView createNew(UUID userId, ImportCommand command, String requestBody) {
        ImportJob existing = jobs.findByUserIdAndClientRequestId(userId, command.clientRequestId()).orElse(null);
        String requestHash = sha256(requestBody);
        if (existing != null) {
            if (!requestHash.equals(existing.getInputHash())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Client request ID was reused");
            }
            return view(existing, quota.current(userId));
        }
        long active = jobs.countByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        if (active >= maxActiveImports) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Maximum active imports reached");
        }
        Instant now = clock.instant();
        boolean upload = command.sourceType() == ImportSourceType.UPLOAD;
        ImportJob job = new ImportJob(
                UUID.randomUUID(), userId, command.clientRequestId(), command.sourceType(), command.sourceUrl(),
                command.mediaKind(), command.fileName(), command.contentType(), command.sizeBytes(),
                command.descriptionText(), requestHash,
                upload ? ImportStatus.AWAITING_UPLOAD : ImportStatus.QUEUED,
                ImportStage.RESOLVING, now.plus(processingTtl), upload ? now.plus(uploadTtl) : null, now);
        jobs.save(job);
        quota.reserve(userId, job.getId());
        return view(job, quota.current(userId));
    }

    ImportView view(ImportJob job, QuotaService.QuotaSnapshot quotaSnapshot) {
        boolean terminal = job.getStatus() == ImportStatus.READY
                || job.getStatus() == ImportStatus.REVIEW_REQUIRED
                || job.getStatus() == ImportStatus.FAILED
                || job.getStatus() == ImportStatus.CANCELLED
                || job.getStatus() == ImportStatus.EXPIRED;
        return new ImportView(
                job.getId(), job.getClientRequestId(), job.getSourceType(), job.getSourceUrl(),
                job.getMediaKind(), job.getFileName(), job.getContentType(), job.getExpectedSizeBytes(),
                job.getDescriptionText(), job.getStatus(), job.getResumeStage(), job.getInputRevision(),
                job.getAttempts(), job.getAttemptStage(), job.getStageAttempts(),
                job.getNextAttemptAt(), job.getProcessingDeadlineAt(),
                job.getInputDeadlineAt(), job.getErrorCode(), job.getCreatedAt(), job.getUpdatedAt(),
                job.getCompletedAt(), terminal ? null : 2, quotaSnapshot);
    }

    private void validate(UUID userId, ImportCommand command) {
        if (userId == null || command == null || command.clientRequestId() == null
                || command.sourceType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid import input");
        }
        if (command.descriptionText() != null && command.descriptionText().length() > maxDescriptionCharacters) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is too long");
        }
        if (command.sourceType() == ImportSourceType.LINK) {
            if (command.sourceUrl() == null || command.sourceUrl().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link source URL is required");
            }
            return;
        }
        if (command.sizeBytes() == null || command.sizeBytes() < 1 || command.mediaKind() == null
                || command.contentType() == null || command.contentType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload metadata is incomplete");
        }
        long maxSize = command.mediaKind() == ImportMediaKind.VIDEO ? maxVideoSizeBytes : maxAudioSizeBytes;
        if (command.sizeBytes() > maxSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload is too large");
        }
    }

    String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
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

    public record ImportCommand(
            UUID clientRequestId,
            ImportSourceType sourceType,
            String sourceUrl,
            ImportMediaKind mediaKind,
            String fileName,
            String contentType,
            Long sizeBytes,
            String descriptionText) {
    }

    public record ImportView(
            UUID id,
            UUID clientRequestId,
            ImportSourceType sourceType,
            String sourceUrl,
            ImportMediaKind mediaKind,
            String fileName,
            String contentType,
            Long sizeBytes,
            String descriptionText,
            ImportStatus status,
            ImportStage resumeStage,
            long inputRevision,
            int attempts,
            ImportStage attemptStage,
            int stageAttempts,
            Instant nextAttemptAt,
            Instant processingDeadlineAt,
            Instant inputDeadlineAt,
            String errorCode,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            Integer pollAfterSeconds,
            QuotaService.QuotaSnapshot quota) {
    }
}
