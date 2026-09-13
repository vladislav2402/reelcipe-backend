package com.reelcipe.auth.domain;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final UserSessionRowMapper userSessionRowMapper;

    public AuthRepository(NamedParameterJdbcTemplate jdbcTemplate, UserSessionRowMapper userSessionRowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userSessionRowMapper = userSessionRowMapper;
    }

    public void create(UserSession userSession) {
        jdbcTemplate.update("""
                INSERT INTO auth_sessions (id, user_id, family_id, refresh_token_hash, expires_at)
                VALUES (:id, :userId, :familyId, :refreshTokenHash, :expiresAt)
                """, userSessionRowMapper.mapToSqlParameters(userSession));
    }

    public Optional<UserSession> findById(UUID sessionId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, family_id, refresh_token_hash, expires_at, revoked_at
                        FROM auth_sessions WHERE id = :id
                        """,
                Map.of("id", sessionId), userSessionRowMapper)
                .stream()
                .findFirst();
    }

    public Optional<UserSession> findByRefreshHash(String refreshTokenHash) {
        return jdbcTemplate.query("""
                SELECT id, user_id, family_id, refresh_token_hash, expires_at, revoked_at
                FROM auth_sessions WHERE refresh_token_hash = :refreshTokenHash FOR UPDATE
                """, Map.of("refreshTokenHash", refreshTokenHash), userSessionRowMapper).stream().findFirst();
    }

    @Transactional
    public void revokeSession(UUID sessionId, Instant now) {
        jdbcTemplate.update("UPDATE auth_sessions SET revoked_at = :now WHERE id = :id AND revoked_at IS NULL",
                Map.of("now", Timestamp.from(now), "id", sessionId));
    }

    @Transactional
    public void revokeFamily(UUID familyId, Instant now) {
        jdbcTemplate.update("UPDATE auth_sessions SET revoked_at = :now WHERE family_id = :familyId AND revoked_at IS NULL",
                Map.of("now", Timestamp.from(now), "familyId", familyId));
    }
}
