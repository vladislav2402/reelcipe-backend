package com.reelcipe.auth.domain;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class UserSessionRowMapper implements RowMapper<UserSession> {

    public SqlParameterSource mapToSqlParameters(UserSession userSession) {
        return new MapSqlParameterSource()
                .addValue("id", userSession.id())
                .addValue("userId", userSession.userId())
                .addValue("familyId", userSession.familyId())
                .addValue("refreshTokenHash", userSession.refreshTokenHash())
                .addValue("expiresAt", Timestamp.from(userSession.expiresAt()));
    }

    @Override
    public UserSession mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new UserSession(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getObject("family_id", UUID.class),
                resultSet.getString("refresh_token_hash"),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getTimestamp("revoked_at") == null ?
                        null : resultSet.getTimestamp("revoked_at").toInstant());
    }
}
