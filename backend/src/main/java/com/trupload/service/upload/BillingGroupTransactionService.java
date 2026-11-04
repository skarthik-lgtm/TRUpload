package com.trupload.service.upload;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BillingGroupTransactionService {

        private static final Pattern SAFE_DATABASE_NAME = Pattern.compile("CSTR[A-Za-z0-9_]*");

        private final JdbcTemplate sqlServerJdbcTemplate;

        public BillingGroupTransactionService(
                        @Qualifier("sqlServerJdbcTemplate") JdbcTemplate sqlServerJdbcTemplate) {
                this.sqlServerJdbcTemplate = sqlServerJdbcTemplate;
        }

        /**
         * Inserts every validated route belonging to one billing group.
         *
         * If any insert fails, the database transaction rolls back
         * all inserts performed by this method.
         */
        @Transactional(transactionManager = "sqlServerTransactionManager", rollbackFor = Exception.class)
        public GroupInsertResult insertSuccessfulGroup(
                        long groupResultId,
                        String databaseName,
                        String routeGroupId,
                        String uploadedBy,
                        boolean shuttle,
                        List<RouteToUpload> routes) {

                if (routes == null || routes.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "At least one validated route is required.");
                }

                String uploadedRouteGroupId = withWaveSuffix(routeGroupId);

                List<RouteToUpload> insertedRoutes = new ArrayList<>();

                int warningCount = 0;

                for (RouteToUpload route : routes) {

                        List<String> warnings = new ArrayList<>();

                        if (shuttle) {
                                warnings.add(
                                                "Shuttle delivery (warehouse destination).");
                        }

                        if (route.dateGapWarning()) {
                                warnings.add(
                                                "Dispatch date is more than 3 days after "
                                                                + "the MMDD date in RouteGroupId.");
                        }

                        String warningText = warnings.isEmpty()
                                        ? null
                                        : String.join(" ", warnings);

                        sqlServerJdbcTemplate.update("""
                                        INSERT INTO [TRUpload].[dbo].[UploadedBilling]
                                            (UploadGroupResultId, DatabaseName, RouteId,
                                             RouteGroupId, DispatchDate, UploadedBy,
                                             IsWeekdayValid, IsDuplicate, IsPastDate,
                                             IsDateGapWarning, IsShuttle, Warnings)
                                        VALUES (?, ?, ?, ?, ?, ?, 1, 0, 0, ?, ?, ?)
                                        """,
                                        groupResultId,
                                        databaseName,
                                        route.routeId(),
                                        uploadedRouteGroupId,
                                        route.dispatchDate(),
                                        uploadedBy,
                                        route.dateGapWarning(),
                                        shuttle,
                                        warningText);

                        insertedRoutes.add(route);

                        if (route.dateGapWarning()) {
                                warningCount++;
                        }
                }

                String groupStatus = warningCount > 0 || shuttle
                                ? "SUCCESS_WITH_WARNINGS"
                                : "SUCCESS";

                sqlServerJdbcTemplate.update("""
                                UPDATE [TRUpload].[dbo].[UploadGroupResult]
                                                                SET RouteGroupId = ?,
                                                                        Status = ?,
                                    CompletedAt = SYSUTCDATETIME(),
                                    RouteCount = ?,
                                    UploadedRouteCount = ?,
                                    FailedRouteCount = 0,
                                    SummaryMessage = ?
                                WHERE UploadGroupResultId = ?
                                """,
                                uploadedRouteGroupId,
                                groupStatus,
                                routes.size(),
                                routes.size(),
                                groupStatus.equals("SUCCESS")
                                                ? "All routes passed blocking validations."
                                                : "Routes accepted with warnings.",
                                groupResultId);

                markSourceRoutesUploaded(databaseName, routeGroupId, routes);

                return new GroupInsertResult(
                                groupStatus,
                                routes.size(),
                                warningCount);
        }

        private String withWaveSuffix(String routeGroupId) {
                return routeGroupId.toLowerCase(java.util.Locale.ROOT).endsWith("wave")
                                ? routeGroupId
                                : routeGroupId + "wave";
        }

        private void markSourceRoutesUploaded(
                        String databaseName,
                        String routeGroupId,
                        List<RouteToUpload> routes) {
                if (!SAFE_DATABASE_NAME.matcher(databaseName).matches()) {
                        throw new IllegalArgumentException("Invalid source database name.");
                }

                String sql = String.format("""
                                      UPDATE [%s].[dbo].[Route]
                                      SET GroupID = CONCAT(RTRIM(GroupID), 'wave')
                                      WHERE RTRIM(GroupID) = ?
                                AND ID = ?
                                AND CAST(CAST(DispatchDateTime AS FLOAT) AS DATETIME) >= ?
                                AND CAST(CAST(DispatchDateTime AS FLOAT) AS DATETIME) < DATEADD(day, 1, ?)
                                        AND LOWER(RTRIM(GroupID)) NOT LIKE '%%wave'
                                      """, databaseName);

                for (RouteToUpload route : routes) {
                        int updatedRows = sqlServerJdbcTemplate.update(
                                        sql,
                                        routeGroupId,
                                        route.routeId(),
                                        java.sql.Date.valueOf(route.dispatchDate()),
                                        java.sql.Date.valueOf(route.dispatchDate()));
                        if (updatedRows != 1) {
                                throw new IllegalStateException(
                                                "Expected to mark one source route as uploaded, but updated "
                                                                + updatedRows + " for route " + route.routeId() + ".");
                        }
                }
        }

        public record RouteToUpload(
                        String routeId,
                        java.time.LocalDate dispatchDate,
                        boolean dateGapWarning) {
        }

        public record GroupInsertResult(
                        String status,
                        int uploadedCount,
                        int warningCount) {
        }
}
