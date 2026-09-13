package com.reelcipe.sync.domain;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

@Repository
public class SyncChangeRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SyncChangeRowMapper rowMapper;

    public SyncChangeRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            SyncChangeRowMapper rowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = rowMapper;
    }

    public long lockAndIncrementSequence(UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO user_sync_state (user_id, last_sequence)
                VALUES (:userId, 0)
                ON CONFLICT (user_id) DO NOTHING
                """, Map.of("userId", userId));

        return jdbcTemplate.queryForObject("""
                UPDATE user_sync_state
                SET last_sequence = last_sequence + 1, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = :userId
                RETURNING last_sequence
                """, Map.of("userId", userId), Long.class);
    }

    public void append(SyncChange change) {
        jdbcTemplate.update("""
                INSERT INTO sync_changes
                    (id, user_id, entity_type, entity_id, operation, version, sequence, created_at)
                VALUES (:id, :userId, :entityType, :entityId, :operation, :version, :sequence, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", change.id())
                .addValue("userId", change.userId())
                .addValue("entityType", change.entityType())
                .addValue("entityId", change.entityId())
                .addValue("operation", change.operation())
                .addValue("version", change.version())
                .addValue("sequence", change.sequence())
                .addValue("createdAt", Timestamp.from(change.createdAt())));
    }

    public long currentSequence(UUID userId) {
        return jdbcTemplate.query(
                        "SELECT last_sequence FROM user_sync_state WHERE user_id = :userId",
                        Map.of("userId", userId),
                        (resultSet, rowNum) -> resultSet.getLong("last_sequence"))
                .stream()
                .findFirst()
                .orElse(0L);
    }

    public long countByUser(UUID userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sync_changes WHERE user_id = :userId",
                Map.of("userId", userId), Long.class);
        return count == null ? 0 : count;
    }
}
