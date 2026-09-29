package com.trupload.service.database;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.trupload.api.database.DatabaseInstanceResponse;

@Service
public class DatabaseInstanceService {

    private final JdbcTemplate sqlServerJdbcTemplate;

   public DatabaseInstanceService(
        @Qualifier("sqlServerJdbcTemplate") JdbcTemplate sqlServerJdbcTemplate) {
    this.sqlServerJdbcTemplate = sqlServerJdbcTemplate;
}

    public List<DatabaseInstanceResponse> findInstances() {
        // Step 1:
        // Get all online databases whose names start with CSTR.
        String databaseQuery = """
                SELECT name
                FROM sys.databases
                WHERE database_id > 4
                  AND state_desc = 'ONLINE'
                  AND name LIKE 'CSTR%'
                ORDER BY name
                """;

        List<String> databaseNames = sqlServerJdbcTemplate.query(
                databaseQuery,
                (resultSet, rowNumber) -> resultSet.getString("name")
        );

        List<DatabaseInstanceResponse> response = new ArrayList<>();

        // Step 2:
        // For every CSTR database, get GroupIDs from dbo.Route.
        for (String databaseName : databaseNames) {

            // Database names come directly from sys.databases,
            // but validate them before using them as SQL identifiers.
            if (!databaseName.matches("[A-Za-z0-9_]+")) {
                continue;
            }

            String groupIdQuery = String.format(
                    """
                    SELECT GroupID
                    FROM [%s].[dbo].[Route]
                    WHERE GroupID IS NOT NULL
                      AND LOWER(RTRIM(GroupID)) NOT LIKE '%%wave'
                    ORDER BY GroupID
                    """,
                    databaseName
            );

            List<String> groupIds = sqlServerJdbcTemplate.query(
                    groupIdQuery,
                    (resultSet, rowNumber) -> resultSet.getString("GroupID")
            );

            response.add(
                    new DatabaseInstanceResponse(
                            databaseName,
                            groupIds
                    )
            );
        }
        return response;
    }
}
