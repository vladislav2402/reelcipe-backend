package com.reelcipe.auth;

import com.reelcipe.auth.domain.AuthRepository;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.role=api", "spring.profiles.active=test"})
@Testcontainers
class AuthRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void repositoriesPersistAndReadSessionsAgainstPostgres() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UserSession session = new UserSession(
                sessionId, userId, familyId, "refresh-hash", Instant.now().plusSeconds(3600), null);

        authRepository.create(session);

        assertThat(userRepository.isActive(userId)).isTrue();
        UserSession storedSession = authRepository.findById(sessionId).orElseThrow();
        assertThat(storedSession.id()).isEqualTo(session.id());
        assertThat(storedSession.userId()).isEqualTo(session.userId());
        assertThat(storedSession.familyId()).isEqualTo(session.familyId());
        assertThat(storedSession.refreshTokenHash()).isEqualTo(session.refreshTokenHash());
        assertThat(storedSession.expiresAt().toEpochMilli()).isEqualTo(session.expiresAt().toEpochMilli());
        assertThat(storedSession.revokedAt()).isNull();

        authRepository.revokeSession(sessionId, Instant.now());

        assertThat(authRepository.findById(sessionId)).get().extracting(UserSession::revokedAt).isNotNull();
    }
}
