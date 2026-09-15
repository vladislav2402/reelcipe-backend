package com.reelcipe.imports;

import com.reelcipe.auth.domain.AuthenticatedUser;
import com.reelcipe.imports.ImportService.ImportCommand;
import com.reelcipe.imports.ImportService.ImportView;
import com.reelcipe.imports.domain.ImportMediaKind;
import com.reelcipe.imports.domain.ImportSourceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/imports")
@Profile("!worker")
@SecurityRequirement(name = "bearerAuth")
public class ImportController {
    private final ImportService service;
    private final ImportLifecycleService lifecycle;
    private final UploadService uploads;

    public ImportController(ImportService service) {
        this(service, null, null);
    }

    public ImportController(ImportService service, ImportLifecycleService lifecycle) {
        this(service, lifecycle, null);
    }

    @Autowired
    public ImportController(
            ImportService service,
            ImportLifecycleService lifecycle,
            UploadService uploads) {
        this.service = service;
        this.lifecycle = lifecycle;
        this.uploads = uploads;
    }

    @PostMapping
    @Operation(summary = "Create an import job")
    public ResponseEntity<ImportView> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ImportRequest request) {
        ImportView view = service.create(requireUser(user).userId(), idempotencyKey, request.toCommand());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(view);
    }

    @GetMapping
    @Operation(summary = "List current user's imports")
    public ResponseEntity<List<ImportView>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.list(requireUser(user).userId()));
    }

    @GetMapping("/{importId}")
    @Operation(summary = "Get an import job")
    public ResponseEntity<ImportView> get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID importId) {
        ImportView view = service.get(requireUser(user).userId(), importId);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (view.status() == com.reelcipe.imports.domain.ImportStatus.RETRY_WAIT && view.nextAttemptAt() != null) {
            long retryAfter = Math.max(1, Duration.between(Instant.now(), view.nextAttemptAt()).toSeconds());
            response.header("Retry-After", String.valueOf(retryAfter));
        }
        return response.body(view);
    }

    @PostMapping("/{importId}/cancel")
    @Operation(summary = "Cancel an import job")
    public ResponseEntity<ImportView> cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID importId) {
        return ResponseEntity.ok(lifecycle.cancel(requireUser(user).userId(), importId));
    }

    @PostMapping("/{importId}/retry")
    @Operation(summary = "Retry a failed import job")
    public ResponseEntity<ImportView> retry(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID importId) {
        return ResponseEntity.ok(lifecycle.retry(requireUser(user).userId(), importId));
    }

    @PostMapping("/{importId}/upload-url")
    @Operation(summary = "Create a presigned upload URL")
    public ResponseEntity<UploadService.UploadUrlResponse> uploadUrl(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID importId) {
        return ResponseEntity.ok(uploads.getUploadUrl(requireUser(user).userId(), importId));
    }

    @PostMapping("/{importId}/upload-complete")
    @Operation(summary = "Confirm an uploaded object")
    public ResponseEntity<UploadService.UploadCompletionResponse> uploadComplete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID importId,
            @Valid @RequestBody UploadCompletionRequest request) {
        return ResponseEntity.ok(uploads.complete(
                requireUser(user).userId(), importId, request.uploadAttemptId()));
    }

    @PostMapping("/{importId}/attach-upload")
    @Operation(summary = "Attach an uploaded object and queue the import")
    public ResponseEntity<UploadService.UploadCompletionResponse> attachUpload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID importId,
            @Valid @RequestBody UploadCompletionRequest request) {
        return ResponseEntity.ok(uploads.attachUpload(
                requireUser(user).userId(), importId, request.uploadAttemptId()));
    }

    private AuthenticatedUser requireUser(AuthenticatedUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return user;
    }

    public record ImportRequest(
            @NotNull UUID clientRequestId,
            @NotNull ImportSourceType sourceType,
            String sourceUrl,
            ImportMediaKind mediaKind,
            String fileName,
            String contentType,
            Long sizeBytes,
            String descriptionText) {
        ImportCommand toCommand() {
            return new ImportCommand(
                    clientRequestId,
                    sourceType,
                    sourceUrl,
                    mediaKind,
                    fileName,
                    contentType,
                    sizeBytes,
                    descriptionText);
        }
    }

    public record UploadCompletionRequest(@NotNull UUID uploadAttemptId) {
    }
}
