package com.reelcipe.auth;

import com.reelcipe.auth.domain.AuthRepository;
import com.reelcipe.auth.domain.AuthenticatedUser;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID FAMILY_ID = UUID.randomUUID();

    @Mock
    private AuthRepository authRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authRepository, userRepository, tokenService, 3600);
    }

    @Test
    void devLoginCreatesCompleteSessionAndReturnsTokens() {
        when(tokenService.issueAccessToken(any(), any(), any())).thenReturn("access-token");

        AuthService.TokenPair result = authService.devLogin("alice");

        assertEquals("access-token", result.accessToken());
        verify(authRepository).create(any(UserSession.class));
    }

    @Test
    void refreshRevokesCurrentSessionAndCreatesRotatedSession() {
        UserSession session = activeSession();
        when(authRepository.findByRefreshHash(any())).thenReturn(Optional.of(session));
        when(tokenService.issueAccessToken(any(), any(), any())).thenReturn("rotated-access-token");

        AuthService.TokenPair result = authService.refresh("refresh-token");

        assertEquals("rotated-access-token", result.accessToken());
        verify(authRepository).revokeSession(any(), any());
        verify(authRepository).create(any(UserSession.class));
    }

    @Test
    void reusedRefreshTokenRevokesItsFamily() {
        UserSession revokedSession = new UserSession(
                SESSION_ID, USER_ID, FAMILY_ID, "hash", Instant.now().plusSeconds(60), Instant.now());
        when(authRepository.findByRefreshHash(any())).thenReturn(Optional.of(revokedSession));

        assertThrows(ResponseStatusException.class, () -> authService.refresh("refresh-token"));

        verify(authRepository).revokeFamily(eq(FAMILY_ID), any());
    }

    @Test
    void requireActiveSessionChecksSessionAndUser() {
        when(authRepository.findById(SESSION_ID)).thenReturn(Optional.of(activeSession()));
        when(userRepository.isActive(USER_ID)).thenReturn(true);

        AuthenticatedUser result = authService.requireActiveSession(
                new JwtTokenService.AuthenticatedToken(USER_ID, SESSION_ID));

        assertEquals(new AuthenticatedUser(USER_ID, SESSION_ID), result);
    }

    private UserSession activeSession() {
        return new UserSession(SESSION_ID, USER_ID, FAMILY_ID, "hash", Instant.now().plusSeconds(3600), null);
    }
}
