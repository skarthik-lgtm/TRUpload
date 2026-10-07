package com.trupload.service.database;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.trupload.api.database.DatabaseInstanceResponse;

@Service
public class DatabaseInstanceService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInstanceService.class);
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

        // Step 2: For every CSTR database, get GroupIDs from dbo.Route.
        for (String databaseName : databaseNames) {

            // Database names come directly from sys.databases,
            // but validate them before using them as SQL identifiers.
            if (!databaseName.matches("[A-Za-z0-9_]+")) {
                continue;
            }

            String groupIdQuery = String.format(
                    """
                    SELECT DISTINCT GroupID
                    FROM [%s].[dbo].[Route]
                    WHERE GroupID IS NOT NULL
                    AND LOWER(RTRIM(GroupID)) NOT LIKE '%%%%wave'
                    -- Chars 1-4 are digits, Char 5 is alpha/character, Chars 6-7 are digits
                    AND GroupID LIKE '[0-9][0-9][0-9][0-9][A-Za-z][0-9][0-9]%%%%'
                    ORDER BY GroupID
                    """,
                    databaseName
            );
                try {
                List<String> groupIds = sqlServerJdbcTemplate.query(
                    groupIdQuery,
                    (resultSet, rowNumber) -> resultSet.getString("GroupID")
                );

                response.add(new DatabaseInstanceResponse(databaseName, groupIds));
                } catch (DataAccessException exception) {
                logger.warn("Skipping SQL Server database {} because it could not be queried: {}",
                    databaseName, exception.getMostSpecificCause().getMessage());
                }
        }
        return response;
    }

    // Test SQL Server connection on application startup
    // public List<DatabaseInstanceResponse> findInstances() {
    //     String serverName = sqlServerJdbcTemplate.queryForObject("SELECT @@SERVERNAME", String.class);
    //     logger.info("Connected to SQL Server: {}", serverName);
    //     return List.of();
    // }

    @EventListener(ApplicationReadyEvent.class)
    public void checkSqlServerConnectionOnStartup() {
        try {
            String serverName = sqlServerJdbcTemplate.queryForObject("SELECT @@SERVERNAME", String.class);
            logger.info("SQL Server connection successful. Connected server: {}", serverName);
        } catch (DataAccessException exception) {
            logger.error("SQL Server connection failed. Verify the SQLSERVER_HOST, SQLSERVER_PORT, SQLSERVER_USERNAME, SQLSERVER_PASSWORD, network access, and SQL Server availability.", exception);
        }
    }
}
