package com.reelcipe.imports;

import com.reelcipe.auth.domain.AuthenticatedUser;
import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.domain.ImportSourceType;
import com.reelcipe.imports.domain.ImportStage;
import com.reelcipe.imports.domain.ImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportControllerTest {
    @Mock
    private ImportService service;

    @Mock
    private ImportLifecycleService lifecycle;

    @Mock
    private UploadService uploads;

    @Test
    void createsImportWithAcceptedStatus() {
        ImportController controller = new ImportController(service);
        UUID userId = UuidV7.randomUuid();
        ImportController.ImportRequest request = new ImportController.ImportRequest(
                UuidV7.randomUuid(), ImportSourceType.LINK, "https://example.com/video",
                null, null, null, null, null);

        ResponseEntity<ImportService.ImportView> response = controller.create(
                new AuthenticatedUser(userId, UuidV7.randomUuid()), "key", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(service).create(userId, "key", request.toCommand());
    }

    @Test
    void rejectsAnonymousCreate() {
        ImportController controller = new ImportController(service);
        ImportController.ImportRequest request = new ImportController.ImportRequest(
                UuidV7.randomUuid(), ImportSourceType.LINK, "https://example.com/video",
                null, null, null, null, null);

        assertThatThrownBy(() -> controller.create(null, "key", request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void listsOnlyThroughServiceForAuthenticatedUser() {
        ImportController controller = new ImportController(service);
        UUID userId = UuidV7.randomUuid();
        when(service.list(userId)).thenReturn(java.util.List.of());

        ResponseEntity<java.util.List<ImportService.ImportView>> response = controller.list(
                new AuthenticatedUser(userId, UuidV7.randomUuid()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(service).list(userId);
    }

    @Test
    void cancelsImportThroughLifecycleService() {
        ImportController controller = new ImportController(service, lifecycle);
        UUID userId = UuidV7.randomUuid();
        UUID importId = UuidV7.randomUuid();

        ResponseEntity<ImportService.ImportView> response = controller.cancel(
                new AuthenticatedUser(userId, UuidV7.randomUuid()), importId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(lifecycle).cancel(userId, importId);
    }

    @Test
    void retriesImportThroughLifecycleService() {
        ImportController controller = new ImportController(service, lifecycle);
        UUID userId = UuidV7.randomUuid();
        UUID importId = UuidV7.randomUuid();

        ResponseEntity<ImportService.ImportView> response = controller.retry(
                new AuthenticatedUser(userId, UuidV7.randomUuid()), importId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(lifecycle).retry(userId, importId);
    }

    @Test
    void exposesRetryAfterForRetryWaitImport() {
        ImportController controller = new ImportController(service);
        UUID userId = UuidV7.randomUuid();
        UUID importId = UuidV7.randomUuid();
        when(service.get(userId, importId)).thenReturn(retryWaitView(importId));

        ResponseEntity<ImportService.ImportView> response = controller.get(
                new AuthenticatedUser(userId, UuidV7.randomUuid()), importId);

        assertThat(response.getHeaders().getFirst("Retry-After")).isNotBlank();
    }

    @Test
    void createsUploadUrlThroughUploadService() {
        ImportController controller = new ImportController(service, lifecycle, uploads);
        UUID userId = UuidV7.randomUuid();
        UUID importId = UuidV7.randomUuid();
        UploadService.UploadUrlResponse expected = new UploadService.UploadUrlResponse(
                importId,
                UuidV7.randomUuid(),
                "https://storage.example/upload",
                Instant.now().plusSeconds(3600),
                100,
                "video/mp4");
        when(uploads.getUploadUrl(userId, importId)).thenReturn(expected);

        ResponseEntity<UploadService.UploadUrlResponse> response = controller.uploadUrl(
                new AuthenticatedUser(userId, UuidV7.randomUuid()), importId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(uploads).getUploadUrl(userId, importId);
    }

    private ImportService.ImportView retryWaitView(UUID importId) {
        return new ImportService.ImportView(
                importId,
                UuidV7.randomUuid(),
                ImportSourceType.LINK,
                "https://example.com/video",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ImportStatus.RETRY_WAIT,
                ImportStage.RESOLVING,
                1,
                1,
                ImportStage.RESOLVING,
                1,
                Instant.now().plusSeconds(30),
                Instant.now().plusSeconds(300),
                null,
                "TEMPORARY_ERROR",
                Instant.now(),
                Instant.now(),
                null,
                30,
                null,
                null);
    }
}
