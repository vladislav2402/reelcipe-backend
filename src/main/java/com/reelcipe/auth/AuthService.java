package com.reelcipe.auth;

import com.reelcipe.auth.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthRepository sessions;
    private final UserRepository users;
    private final JwtTokenService tokens;
    private final long refreshTokenTtlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AuthRepository sessions,
            UserRepository users,
            JwtTokenService tokens,
            @Value("${app.auth.refresh-token-ttl-seconds:2592000}") long refreshTokenTtlSeconds) {
        this.sessions = sessions;
        this.users = users;
        this.tokens = tokens;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Transactional
    public TokenPair devLogin(String fixture) {
        String username = fixture == null ? "" : fixture.trim();
        if (username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dev username is required");
        }

        UUID userId = switch (username.toLowerCase()) {
            case "alice", "fixture-alice" -> FixtureUserInitializer.ALICE_ID;
            case "bob", "fixture-bob" -> FixtureUserInitializer.BOB_ID;
            default -> createDevUser(username);
        };
        return createSession(userId);
    }

    private UUID createDevUser(String username) {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        users.save(new User(userId, username, UserStatus.ACTIVE, now, now));
        return userId;
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is required");
        }
        Instant now = Instant.now();
        UserSession current = sessions.findLockedByRefreshTokenHash(TokenHash.sha256(refreshToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (!current.isActive(now)) {
            sessions.revokeFamily(current.familyId(), now);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is no longer active");
        }
        sessions.revokeSession(current.id(), now);
        return createSession(current.userId(), current.familyId(), now);
    }

    @Transactional
    public void logout(AuthenticatedUser user) {
        sessions.revokeSession(user.sessionId(), Instant.now());
    }

    public AuthenticatedUser requireActiveSession(JwtTokenService.AuthenticatedToken token) {
        UserSession session = sessions.findById(token.sessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session is not active"));
        if (!session.isActive(Instant.now()) || !session.userId().equals(token.userId())
                || users.countByIdAndStatus(token.userId(), com.reelcipe.auth.domain.UserStatus.ACTIVE) != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session is not active");
        }
        return new AuthenticatedUser(token.userId(), token.sessionId());
    }

    public UserProfile profile(AuthenticatedUser user) {
        return users.findByIdAndStatus(user.userId(), com.reelcipe.auth.domain.UserStatus.ACTIVE)
                .map(profile -> new UserProfile(
                        profile.getId(), profile.getDisplayName(), profile.getStatus(), profile.getCreatedAt()))
                .orElse(null);
    }

    private TokenPair createSession(UUID userId) {
        Instant now = Instant.now();
        return createSession(userId, UUID.randomUUID(), now);
    }

    private TokenPair createSession(UUID userId, UUID familyId, Instant now) {
        String refreshToken = randomToken();
        UserSession session = new UserSession(userId, familyId, TokenHash.sha256(refreshToken),
                now.plusSeconds(refreshTokenTtlSeconds));
        sessions.save(session);
        return new TokenPair(tokens.issueAccessToken(userId, session.id(), now), refreshToken);
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }

}
