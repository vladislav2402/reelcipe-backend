package com.reelcipe.shopping;

import com.reelcipe.auth.domain.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ShoppingControllerTest {
    @Mock
    ShoppingService service;

    @Test
    void patchRejectsMalformedIfMatch() {
        ShoppingController controller = new ShoppingController(service);

        assertThrows(ResponseStatusException.class, () -> controller.patchItem(
                new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(),
                "bad",
                "key",
                new ShoppingController.ItemPatchRequest(null, null, null, true)));
    }
}
