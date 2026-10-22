package com.trupload.service.upload;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BillingGroupTransactionService {

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
                    routeGroupId,
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
                SET Status = ?,
                    CompletedAt = SYSUTCDATETIME(),
                    RouteCount = ?,
                    UploadedRouteCount = ?,
                    FailedRouteCount = 0,
                    SummaryMessage = ?
                WHERE UploadGroupResultId = ?
                """,
                groupStatus,
                routes.size(),
                routes.size(),
                groupStatus.equals("SUCCESS")
                        ? "All routes passed blocking validations."
                        : "Routes accepted with warnings.",
                groupResultId);

        return new GroupInsertResult(
                groupStatus,
                routes.size(),
                warningCount);
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
