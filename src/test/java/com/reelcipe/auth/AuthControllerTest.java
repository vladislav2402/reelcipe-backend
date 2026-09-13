package com.reelcipe.auth;

import com.reelcipe.auth.domain.AuthenticatedUser;
import com.reelcipe.auth.domain.UserProfile;
import com.reelcipe.auth.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtTokenService tokenService;

    @Test
    void devLoginMapsServicePairToResponse() {
        AuthController controller = new AuthController(authService, tokenService);
        when(authService.devLogin("alice")).thenReturn(new AuthService.TokenPair("access", "refresh"));
        when(tokenService.accessTokenTtlSeconds()).thenReturn(900L);

        AuthController.TokenResponse response = controller.devLogin(new AuthController.DevLoginRequest("alice"));

        assertEquals("Bearer", response.tokenType());
        assertEquals("access", response.accessToken());
        assertEquals("refresh", response.refreshToken());
        assertEquals(900L, response.expiresInSeconds());
    }

    @Test
    void meReturnsProfile() {
        AuthController controller = new AuthController(authService, tokenService);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID());
        UserProfile profile = new UserProfile(user.userId(), "Alice", UserStatus.ACTIVE, Instant.now());
        when(authService.profile(user)).thenReturn(profile);

        AuthController.MeResponse response = controller.me(user);

        assertEquals(profile.id(), response.id());
        assertEquals(profile.displayName(), response.displayName());
    }

    @Test
    void meRejectsMissingAuthentication() {
        AuthController controller = new AuthController(authService, tokenService);

        assertThrows(ResponseStatusException.class, () -> controller.me(null));
    }
}
