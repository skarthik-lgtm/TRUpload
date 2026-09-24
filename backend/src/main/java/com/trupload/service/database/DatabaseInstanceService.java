package com.trupload.service.database;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.trupload.api.database.DatabaseInstanceResponse;

@Service
public class DatabaseInstanceService {

    private final JdbcTemplate sqlServerJdbcTemplate;

    public DatabaseInstanceService(JdbcTemplate sqlServerJdbcTemplate) {
        this.sqlServerJdbcTemplate = sqlServerJdbcTemplate;
    }

    public List<DatabaseInstanceResponse> findInstances() {
        return sqlServerJdbcTemplate.query(
                "SELECT name, state_desc, create_date "
                        + "FROM sys.databases WHERE database_id > 4 ORDER BY name",
                (resultSet, rowNumber) -> {
                    Timestamp createdAt = resultSet.getTimestamp("create_date");
                    OffsetDateTime createdAtValue = createdAt == null
                            ? null
                            : createdAt.toInstant().atOffset(ZoneOffset.UTC);
                    return new DatabaseInstanceResponse(
                            resultSet.getString("name"),
                            resultSet.getString("state_desc"),
                            createdAtValue);
                });
    }
}
