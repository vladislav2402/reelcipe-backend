package com.reelcipe.auth.domain;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final UserProfileRowMapper userProfileRowMapper;

    public UserRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            UserProfileRowMapper userProfileRowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userProfileRowMapper = userProfileRowMapper;
    }

    public boolean isActive(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = :id AND status = 'ACTIVE'",
                Map.of("id", userId), Integer.class);
        return count != null && count == 1;
    }

    public Optional<UserProfile> findActiveProfile(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT id, display_name, status, created_at
                        FROM users
                        WHERE id = :id AND status = 'ACTIVE'
                        """,
                Map.of("id", userId), userProfileRowMapper).stream().findFirst();
    }
}
