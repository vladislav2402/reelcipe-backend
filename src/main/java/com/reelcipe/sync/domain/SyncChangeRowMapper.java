package com.reelcipe.sync.domain;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Component
public class SyncChangeRowMapper implements RowMapper<SyncChange> {

    @Override
    public SyncChange mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SyncChange(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("entity_type"),
                resultSet.getObject("entity_id", UUID.class),
                resultSet.getString("operation"),
                resultSet.getLong("version"),
                resultSet.getLong("sequence"),
                resultSet.getTimestamp("created_at").toInstant());
    }
}
