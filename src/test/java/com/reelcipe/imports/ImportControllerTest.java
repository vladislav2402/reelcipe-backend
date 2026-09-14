package com.reelcipe.imports;

import com.reelcipe.auth.domain.AuthenticatedUser;
import com.reelcipe.imports.domain.ImportSourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportControllerTest {
    @Mock
    private ImportService service;

    @Test
    void createsImportWithAcceptedStatus() {
        ImportController controller = new ImportController(service);
        UUID userId = UUID.randomUUID();
        ImportController.ImportRequest request = new ImportController.ImportRequest(
                UUID.randomUUID(), ImportSourceType.LINK, "https://example.com/video",
                null, null, null, null, null);

        ResponseEntity<ImportService.ImportView> response = controller.create(
                new AuthenticatedUser(userId, UUID.randomUUID()), "key", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(service).create(userId, "key", request.toCommand());
    }

    @Test
    void rejectsAnonymousCreate() {
        ImportController controller = new ImportController(service);
        ImportController.ImportRequest request = new ImportController.ImportRequest(
                UUID.randomUUID(), ImportSourceType.LINK, "https://example.com/video",
                null, null, null, null, null);

        assertThatThrownBy(() -> controller.create(null, "key", request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void listsOnlyThroughServiceForAuthenticatedUser() {
        ImportController controller = new ImportController(service);
        UUID userId = UUID.randomUUID();
        when(service.list(userId)).thenReturn(java.util.List.of());

        ResponseEntity<java.util.List<ImportService.ImportView>> response = controller.list(
                new AuthenticatedUser(userId, UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(service).list(userId);
    }
}
