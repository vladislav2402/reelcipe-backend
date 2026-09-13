package com.reelcipe.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("!production")
public class FixtureUserInitializer {

    public static final UUID ALICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FixtureUserInitializer(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void ensureFixtureUsers() {
        insertIfMissing(ALICE_ID, "fixture-alice", "Fixture Alice");
        insertIfMissing(BOB_ID, "fixture-bob", "Fixture Bob");
    }

    private void insertIfMissing(UUID id, String appleSubject, String displayName) {
        jdbcTemplate.update("""
                INSERT INTO users (id, apple_subject, display_name, status)
                VALUES (:id, :appleSubject, :displayName, 'ACTIVE')
                ON CONFLICT (id) DO NOTHING
                """, java.util.Map.of(
                "id", id,
                "appleSubject", appleSubject,
                "displayName", displayName));
    }
}
