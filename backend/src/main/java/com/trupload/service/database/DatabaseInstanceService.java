package com.trupload.service.database;

import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.net.SocketException;
import java.sql.SQLException;

import com.trupload.api.database.OrderResponse;
import com.trupload.api.database.RouteActivityResponse;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.trupload.api.database.DatabaseInstanceResponse;
// import com.trupload.api.database.RouteResponse;
import com.trupload.api.database.RouteGroupResponse;
import com.trupload.api.database.RouteDetailResponse;
import com.trupload.api.database.OrderFacilityResponse;

@Service
public class DatabaseInstanceService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInstanceService.class);
    private final JdbcTemplate sqlServerJdbcTemplate;
    private final Map<String, CachedRoutes> routeCache = new ConcurrentHashMap<>();
    private volatile CachedDatabaseNames databaseNamesCache;

    public DatabaseInstanceService(
            @Qualifier("sqlServerJdbcTemplate") JdbcTemplate sqlServerJdbcTemplate) {
        this.sqlServerJdbcTemplate = sqlServerJdbcTemplate;
    }

    public List<DatabaseInstanceResponse> findInstances() {
        return findInstances(null);
    }

    public List<DatabaseInstanceResponse> findInstances(Set<String> allowedDatabaseNames) {
        return findInstances(allowedDatabaseNames, null);
    }

    public List<DatabaseInstanceResponse> findInstances(
            Set<String> allowedDatabaseNames,
            Set<String> allowedFacilityIds) {
        List<String> databaseNames = getDatabaseNames();
        List<DatabaseInstanceResponse> response = new ArrayList<>();

        for (String databaseName : databaseNames) {

            if (allowedDatabaseNames != null && !allowedDatabaseNames.contains(databaseName)) {
                continue;
            }

            if (databaseName == null
                    || !databaseName.matches("[A-Za-z0-9_]+")) {
                continue;
            }

            try {
                CachedRoutes cachedRoutes = getCachedRoutes(databaseName);
                List<RouteGroupResponse> routeGroups = cachedRoutes.routeGroups();
                if (allowedFacilityIds != null) {
                    routeGroups = routeGroups.stream()
                            .filter(group -> group.orderFacilities().stream()
                                    .anyMatch(facility -> allowedFacilityIds.contains(facility.sourceFacilityId())))
                            .toList();
                }
                response.add(new DatabaseInstanceResponse(
                        databaseName,
                        routeGroups,
                        cachedRoutes.refreshedAt()));

            } catch (DataAccessException exception) {
                Throwable cause = exception.getMostSpecificCause();
                SQLException sqlException = findSqlException(exception);
                logger.warn(
                        "Skipping SQL Server database {} because it could not be queried. SQL state: {}, error code: {}, cause: {}",
                        databaseName,
                        sqlException == null ? "unknown" : sqlException.getSQLState(),
                        sqlException == null ? "unknown" : sqlException.getErrorCode(),
                        cause.getMessage());
            }
        }

        return response;
    }

    private List<String> getDatabaseNames() {
        CachedDatabaseNames current = databaseNamesCache;
        Instant now = Instant.now();
        if (current != null && current.refreshedAt().plus(CACHE_TTL).isAfter(now)) {
            return current.names();
        }

        synchronized (this) {
            current = databaseNamesCache;
            now = Instant.now();
            if (current != null && current.refreshedAt().plus(CACHE_TTL).isAfter(now)) {
                return current.names();
            }

            String databaseQuery = """
                        SELECT DISTINCT LTRIM(RTRIM([database])) AS DatabaseName
                        FROM [TRUpload].[dbo].[Warehouses]
                        WHERE [database] IS NOT NULL
                            AND LTRIM(RTRIM([database])) <> ''
                        ORDER BY DatabaseName
                    """;
            List<String> names = sqlServerJdbcTemplate.query(
                    databaseQuery,
                    (resultSet, rowNumber) -> resultSet.getString("DatabaseName"));
            databaseNamesCache = new CachedDatabaseNames(List.copyOf(names), Instant.now());
            return databaseNamesCache.names();
        }
    }

    private CachedRoutes getCachedRoutes(String databaseName) {
        CachedRoutes cached = routeCache.get(databaseName);
        Instant now = Instant.now();

        // 1. Check for a valid cache entry.
        if (cached != null &&
                cached.refreshedAt().plus(CACHE_TTL).isAfter(now)) {

            logger.info(
                    "CACHE HIT: database={}, refreshedAt={}",
                    databaseName,
                    cached.refreshedAt());

            return cached;
        }

        // 2. Synchronize refreshes so concurrent requests don't
        // independently refresh the same cache.
        synchronized (routeCache) {
            cached = routeCache.get(databaseName);
            now = Instant.now();

            // Another request may have refreshed it while we waited.
            if (cached != null &&
                    cached.refreshedAt().plus(CACHE_TTL).isAfter(now)) {

                logger.info(
                        "CACHE HIT after lock: database={}, refreshedAt={}",
                        databaseName,
                        cached.refreshedAt());

                return cached;
            }

            logger.info(
                    "CACHE MISS/EXPIRED: refreshing database={}",
                    databaseName);

            // 3. Run the existing consolidation logic.
            List<RouteGroupResponse> routeGroups = findRoutesForDatabaseWithRetry(databaseName);

            // 4. Store the new result and its refresh timestamp.
            CachedRoutes refreshed = new CachedRoutes(List.copyOf(routeGroups), Instant.now());

            routeCache.put(databaseName, refreshed);

            logger.info(
                    "CACHE REFRESHED: database={}, groups={}, refreshedAt={}",
                    databaseName,
                    routeGroups.size(),
                    refreshed.refreshedAt());

            return refreshed;
        }
    }

    private record CachedDatabaseNames(List<String> names, Instant refreshedAt) {
    }

    private record CachedRoutes(List<RouteGroupResponse> routeGroups, Instant refreshedAt) {
    }

    private List<RouteGroupResponse> findRoutesForDatabaseWithRetry(
            String databaseName) {

        for (int attempt = 1;; attempt++) {
            try {
                return findRoutesForDatabase(databaseName);
            } catch (DataAccessException exception) {
                if (attempt >= 2 || !isConnectionFailure(exception)) {
                    throw exception;
                }

                logger.warn(
                        "Transient SQL Server connection failure querying {}; retrying once",
                        databaseName);
            }
        }
    }

    private boolean isConnectionFailure(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketException) {
                return true;
            }
            if (cause instanceof SQLException sqlException
                    && sqlException.getSQLState() != null
                    && sqlException.getSQLState().startsWith("08")) {
                return true;
            }
            if (cause.getMessage() != null
                    && cause.getMessage().toLowerCase().contains("connection reset")) {
                return true;
            }
        }
        return false;
    }

    private SQLException findSqlException(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException;
            }
        }
        return null;
    }

    private List<RouteGroupResponse> findRoutesForDatabase(
            String databaseName) {

        if (databaseName == null
                || !databaseName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid database name");
        }

        /*
         * STEP 1:
         * Fetch every eligible Route record, including its dispatch time.
         * Do not use DISTINCT because route records must remain available
         * for validation.
         */
        String routeQuery = String.format("""
                SELECT
                    r.ID AS RouteID,
                    r.GroupID AS RouteGroupID,
                    CAST(
                        CAST(r.DispatchDateTime AS FLOAT) AS DATETIME
                    ) AS DispatchDateTime
                FROM [%s].[dbo].[Route] r
                WHERE r.GroupID IS NOT NULL
                  AND LOWER(RTRIM(r.GroupID)) NOT LIKE '%%wave'
                  AND r.GroupID LIKE
                      '[0-9][0-9][0-9][0-9][A-Za-z][0-9][0-9]%%'
                ORDER BY r.GroupID, r.ID, r.DispatchDateTime
                """, databaseName);

        List<RouteDetailResponse> routeRows = sqlServerJdbcTemplate.query(routeQuery, (rs, rowNum) -> {

            String routeId = rs.getString("RouteID");
            String groupId = rs.getString("RouteGroupID");

            java.sql.Timestamp timestamp = rs.getTimestamp("DispatchDateTime");

            java.time.LocalDateTime dispatchDateTime = timestamp == null
                    ? null
                    : timestamp.toLocalDateTime();

            Integer expectedWeekday = null;

            if (routeId != null && !routeId.isEmpty()) {
                char firstChar = routeId.trim().charAt(0);

                if (firstChar >= '1' && firstChar <= '7') {
                    expectedWeekday = firstChar - '0';
                }
            }

            Integer actualWeekday = null;

            if (dispatchDateTime != null) {
                // Java weekday numbering is Monday=1 through Sunday=7.
                actualWeekday = dispatchDateTime.getDayOfWeek().getValue();
            }

            String expectedDayName = weekdayName(expectedWeekday);

            String actualDayName = weekdayName(actualWeekday);

            String result = "";

            if (expectedWeekday != null && actualWeekday != null) {
                result = expectedWeekday.equals(actualWeekday)
                        ? "MATCH"
                        : "NON-MATCH";
            }

            return new RouteDetailResponse(
                    routeId,
                    groupId,
                    dispatchDateTime,
                    expectedDayName,
                    actualDayName,
                    result);
        });

        if (routeRows.isEmpty()) {
            return List.of();
        }

        /*
         * STEP 2:
         * Aggregate ScheduledOrder independently.
         *
         * COUNT(*) counts source records, not necessarily distinct
         * business orders.
         */
        String orderQuery = String.format("""
                SELECT
                    so.RouteGroupID,
                    COUNT(*) AS NumberOfOrders,
                    SUM(COALESCE(so.Q1, 0)) AS TotalQ1,
                    SUM(COALESCE(so.Q2, 0)) AS TotalQ2,
                    SUM(COALESCE(so.Q3, 0)) AS TotalQ3,
                    SUM(COALESCE(so.Q4, 0)) AS TotalQ4
                FROM [%s].[dbo].[ScheduledOrder] so
                WHERE so.RouteGroupID IS NOT NULL
                  AND EXISTS (
                      SELECT 1
                      FROM [%s].[dbo].[Route] r
                      WHERE r.GroupID = so.RouteGroupID
                        AND r.GroupID IS NOT NULL
                        AND LOWER(RTRIM(r.GroupID)) NOT LIKE '%%wave'
                        AND r.GroupID LIKE
                            '[0-9][0-9][0-9][0-9][A-Za-z][0-9][0-9]%%'
                  )
                GROUP BY so.RouteGroupID
                """, databaseName, databaseName);

        Map<String, OrderTotals> orderTotalsMap = new java.util.HashMap<>();

        sqlServerJdbcTemplate.query(orderQuery, rs -> {

            orderTotalsMap.put(
                    rs.getString("RouteGroupID"),
                    new OrderTotals(
                            rs.getLong("NumberOfOrders"),
                            rs.getBigDecimal("TotalQ1"),
                            rs.getBigDecimal("TotalQ2"),
                            rs.getBigDecimal("TotalQ3"),
                            rs.getBigDecimal("TotalQ4")));
        });

        String orderFacilityQuery = String.format("""
                 SELECT DISTINCT
                     orders.GroupID AS RouteGroupID,
                     orders.SourceID AS SourceID,
                     sourceFacility.fAC_id AS SourceFacilityId,
                     sourceFacility.KeyCode AS SourceFacilityCode,
                     sourceFacility.Facility AS SourceName,
                     orders.DestinationID AS DestinationID,
                     sourceFacility.Division AS Division
                 FROM [%s].[dbo].[Orders] orders

                 LEFT JOIN [OrderDownload].[dbo].[tblDatabaseControl] sourceFacility
                     ON TRY_CONVERT(INT, sourceFacility.fAC_id)
                = TRY_CONVERT(INT, orders.SourceID)
                AND NULLIF(LTRIM(RTRIM(sourceFacility.KeyCode)), '') IS NOT NULL

                 WHERE orders.GroupID IS NOT NULL
                   AND EXISTS (
                       SELECT 1
                       FROM [%s].[dbo].[Route] r
                       WHERE r.GroupID = orders.GroupID
                         AND r.GroupID IS NOT NULL
                         AND LOWER(RTRIM(r.GroupID)) NOT LIKE '%%wave'
                         AND r.GroupID LIKE
                             '[0-9][0-9][0-9][0-9][A-Za-z][0-9][0-9]%%'
                   )
                 ORDER BY orders.GroupID, orders.SourceID, orders.DestinationID
                 """, databaseName, databaseName);

        Map<String, LinkedHashSet<OrderFacilityResponse>> orderFacilitiesMap = new LinkedHashMap<>();

        try {
            sqlServerJdbcTemplate.query(orderFacilityQuery, rs -> {
                String routeGroupId = rs.getString("RouteGroupID");

                OrderFacilityResponse facility = new OrderFacilityResponse(
                        rs.getString("SourceID"),
                        rs.getString("SourceFacilityId"),
                        rs.getString("SourceFacilityCode"),
                        rs.getString("SourceName"),
                        rs.getString("DestinationID"),
                        rs.getString("Division"));

                orderFacilitiesMap
                        .computeIfAbsent(routeGroupId, ignored -> new LinkedHashSet<>())
                        .add(facility);
            });
        } catch (DataAccessException exception) {
            logger.warn(
                    "Could not map source/destination facilities from OrderDownload for database {}. Route and order totals will still be returned: {}",
                    databaseName,
                    exception.getMostSpecificCause().getMessage());
        }

        /*
         * STEP 3:
         * Count qualifying RouteActivity records independently.
         * EXISTS prevents duplicate Route rows from multiplying activity
         * counts if the source contains duplicate route keys.
         */
        String stopQuery = String.format("""
                SELECT
                    ra.RouteGroupID,
                    COUNT(*) AS NumberOfStops
                FROM [%s].[dbo].[RouteActivity] ra
                WHERE ra.RouteStopIndex = 7
                  AND EXISTS (
                      SELECT 1
                      FROM [%s].[dbo].[Route] r
                      WHERE r.ID = ra.RouteID
                        AND r.GroupID = ra.RouteGroupID
                        AND r.GroupID IS NOT NULL
                        AND LOWER(RTRIM(r.GroupID)) NOT LIKE '%%wave'
                        AND r.GroupID LIKE
                            '[0-9][0-9][0-9][0-9][A-Za-z][0-9][0-9]%%'
                  )
                GROUP BY ra.RouteGroupID
                """, databaseName, databaseName);

        Map<String, Long> stopCountsMap = new java.util.HashMap<>();

        sqlServerJdbcTemplate.query(stopQuery, rs -> {
            stopCountsMap.put(
                    rs.getString("RouteGroupID"),
                    rs.getLong("NumberOfStops"));
        });

        /*
         * STEP 4:
         * Group route details by RouteGroupID.
         * Each source Route record is retained in the routes list.
         */
        Map<String, RouteGroupBuilder> groupMap = new LinkedHashMap<>();

        for (RouteDetailResponse route : routeRows) {

            RouteGroupBuilder group = groupMap.computeIfAbsent(
                    route.routeGroupId(),
                    RouteGroupBuilder::new);

            group.routes.add(route);

            if ("NON-MATCH".equals(route.result())) {
                group.validationErrors.add(
                        "Route " + route.routeId()
                                + " has a dispatch weekday that does not match "
                                + "the first digit of its RouteID.");
            }
        }

        /*
         * STEP 5:
         * Assemble one summary response per RouteGroupID.
         */
        List<RouteGroupResponse> result = new ArrayList<>();

        Set<String> processedGroupIds = new java.util.HashSet<>(sqlServerJdbcTemplate.query("""
                SELECT RouteGroupId
                FROM [TRUpload].[dbo].[UploadedBilling]
                WHERE DatabaseName = ?
                UNION
                SELECT RouteGroupId
                FROM [TRUpload].[dbo].[UploadFailedBilling]
                WHERE DatabaseName = ?
                """, (rs, rowNum) -> rs.getString("RouteGroupId"), databaseName, databaseName));

        for (RouteGroupBuilder group : groupMap.values()) {

            if (processedGroupIds.contains(group.routeGroupId)) {
                continue;
            }

            OrderTotals totals = orderTotalsMap.get(group.routeGroupId);

            long numberOfOrders = totals == null ? 0 : totals.numberOfOrders();

            java.math.BigDecimal totalQ1 = totals == null ? java.math.BigDecimal.ZERO : totals.totalQ1();

            java.math.BigDecimal totalQ2 = totals == null ? java.math.BigDecimal.ZERO : totals.totalQ2();

            java.math.BigDecimal totalQ3 = totals == null ? java.math.BigDecimal.ZERO : totals.totalQ3();

            java.math.BigDecimal totalQ4 = totals == null ? java.math.BigDecimal.ZERO : totals.totalQ4();

            long numberOfStops = stopCountsMap.getOrDefault(
                    group.routeGroupId, 0L);

            result.add(new RouteGroupResponse(
                    group.routeGroupId,
                    group.routes.size(),
                    numberOfStops,
                    numberOfOrders,
                    totalQ1,
                    totalQ2,
                    totalQ3,
                    totalQ4,
                    group.routes,
                    List.copyOf(orderFacilitiesMap.getOrDefault(
                            group.routeGroupId,
                            new LinkedHashSet<>())),
                    group.validationErrors));
        }

        return result;
    }

    private String weekdayName(Integer weekday) {
        if (weekday == null) {
            return "";
        }

        return switch (weekday) {
            case 1 -> "Monday";
            case 2 -> "Tuesday";
            case 3 -> "Wednesday";
            case 4 -> "Thursday";
            case 5 -> "Friday";
            case 6 -> "Saturday";
            case 7 -> "Sunday";
            default -> "";
        };
    }

    private record OrderTotals(
            long numberOfOrders,
            java.math.BigDecimal totalQ1,
            java.math.BigDecimal totalQ2,
            java.math.BigDecimal totalQ3,
            java.math.BigDecimal totalQ4) {
    }

    private static class RouteGroupBuilder {
        private final String routeGroupId;
        private final List<RouteDetailResponse> routes = new ArrayList<>();
        private final List<String> validationErrors = new ArrayList<>();

        private RouteGroupBuilder(String routeGroupId) {
            this.routeGroupId = routeGroupId;
        }
    }

    private String routeKey(String routeId, String groupId) {
        return routeId + "||" + groupId;
    }

    private record RouteRow(String routeId, String groupId) {
    }

    private record OrderRow(
            String routeId,
            String groupId,
            OrderResponse order) {
    }

    private record ActivityRow(
            String routeId,
            String groupId,
            RouteActivityResponse activity) {
    }

    private static class RouteBuilder {
        private final String routeId;
        private final String groupId;
        private final List<OrderResponse> orders = new ArrayList<>();
        private final List<RouteActivityResponse> routeActivities = new ArrayList<>();

        private RouteBuilder(String routeId, String groupId) {
            this.routeId = routeId;
            this.groupId = groupId;
        }
    }

    public DatabaseInstanceResponse findRoutes(String databaseName) {

        if (databaseName == null
                || !databaseName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid database name");
        }

        CachedRoutes cachedRoutes = getCachedRoutes(databaseName);
        return new DatabaseInstanceResponse(
                databaseName,
                cachedRoutes.routeGroups(),
                cachedRoutes.refreshedAt());
    }

    public void invalidateRouteCache(String databaseName) {
        routeCache.remove(databaseName);
    }
    // Test SQL Server connection on application startup
    // public List<DatabaseInstanceResponse> findInstances() {
    // String serverName = sqlServerJdbcTemplate.queryForObject("SELECT
    // @@SERVERNAME", String.class);
    // logger.info("Connected to SQL Server: {}", serverName);
    // return List.of();
    // }

    @EventListener(ApplicationReadyEvent.class)
    public void checkSqlServerConnectionOnStartup() {
        try {
            String serverName = sqlServerJdbcTemplate.queryForObject("SELECT @@SERVERNAME", String.class);
            logger.info("SQL Server connection successful. Connected server: {}", serverName);
        } catch (DataAccessException exception) {
            logger.error(
                    "SQL Server connection failed. Verify the SQLSERVER_HOST, SQLSERVER_PORT, SQLSERVER_USERNAME, SQLSERVER_PASSWORD, network access, and SQL Server availability.",
                    exception);
        }
    }
}
