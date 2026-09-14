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
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/imports")
@Profile("!worker")
@SecurityRequirement(name = "bearerAuth")
public class ImportController {
    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
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
        return ResponseEntity.ok(service.get(requireUser(user).userId(), importId));
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
}
