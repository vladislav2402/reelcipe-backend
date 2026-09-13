package com.reelcipe.auth;

import com.reelcipe.auth.domain.AuthenticatedUser;
import com.reelcipe.auth.domain.UserProfile;
import com.reelcipe.auth.domain.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@Profile("!production")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenService tokenService;

    public AuthController(AuthService authService, JwtTokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/auth/dev")
    @Operation(summary = "Local development login")
    public TokenResponse devLogin(@Valid @RequestBody DevLoginRequest request) {
        AuthService.TokenPair pair = authService.devLogin(request.fixture());
        return TokenResponse.from(pair, tokenService.accessTokenTtlSeconds());
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "Rotate a refresh token")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        AuthService.TokenPair pair = authService.refresh(request.refreshToken());
        return TokenResponse.from(pair, tokenService.accessTokenTtlSeconds());
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "Revoke the current session")
    @SecurityRequirement(name = "bearerAuth")
    public void logout(@AuthenticationPrincipal AuthenticatedUser user) {
        authService.logout(requireUser(user));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user")
    @SecurityRequirement(name = "bearerAuth")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        UserProfile profile = authService.profile(requireUser(user));
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not active");
        }
        return new MeResponse(profile.id(), profile.displayName(), profile.status(), profile.createdAt());
    }

    private AuthenticatedUser requireUser(AuthenticatedUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return user;
    }

    public record DevLoginRequest(@NotBlank String fixture) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(
            String tokenType,
            String accessToken,
            String refreshToken,
            long expiresInSeconds) {

        static TokenResponse from(AuthService.TokenPair pair, long expiresInSeconds) {
            return new TokenResponse("Bearer", pair.accessToken(), pair.refreshToken(), expiresInSeconds);
        }
    }

    public record MeResponse(UUID id, String displayName, UserStatus status, Instant createdAt) {
    }
}
