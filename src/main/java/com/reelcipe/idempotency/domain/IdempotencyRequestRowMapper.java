package com.reelcipe.idempotency.domain;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Component
public class IdempotencyRequestRowMapper implements RowMapper<IdempotencyRequest> {

    @Override
    public IdempotencyRequest mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new IdempotencyRequest(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("operation"),
                resultSet.getString("target"),
                resultSet.getString("idempotency_key"),
                resultSet.getString("request_hash"),
                (Integer) resultSet.getObject("response_status"),
                resultSet.getString("response_body"),
                resultSet.getString("response_content_type"),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getTimestamp("completed_at") == null
                        ? null
                        : resultSet.getTimestamp("completed_at").toInstant());
    }
}
