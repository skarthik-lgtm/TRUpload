package com.trupload.service.upload;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.trupload.api.upload.FailedUploadRecord;
import com.trupload.api.upload.UploadRecordResult;
import com.trupload.api.upload.UploadRequest;
import com.trupload.api.upload.UploadResponse;
import com.trupload.api.upload.UploadSelection;
import com.trupload.api.upload.SuccessfulUploadRecord;
import com.trupload.service.database.DatabaseInstanceService;
import jakarta.annotation.PostConstruct;

@Service
public class BillingUploadService {

        private static final Pattern SAFE_DATABASE_NAME = Pattern.compile("CSTR[A-Za-z0-9_]*");
        private final JdbcTemplate sqlServerJdbcTemplate;
        private final BillingGroupTransactionService billingGroupTransactionService;
        private final DatabaseInstanceService databaseInstanceService;
        private final BillingRouteValidator routeValidator = new BillingRouteValidator();

        public BillingUploadService(
                        @Qualifier("sqlServerJdbcTemplate") JdbcTemplate sqlServerJdbcTemplate,
                        BillingGroupTransactionService billingGroupTransactionService,
                        DatabaseInstanceService databaseInstanceService) {
                this.sqlServerJdbcTemplate = sqlServerJdbcTemplate;
                this.billingGroupTransactionService = billingGroupTransactionService;
                this.databaseInstanceService = databaseInstanceService;
        }

        @PostConstruct
        public void ensureUploadTablesExist() {

                String sql = """
                                SELECT CASE
                                    WHEN OBJECT_ID(N'TRUpload.dbo.UploadBatch', N'U') IS NOT NULL
                                    AND OBJECT_ID(N'TRUpload.dbo.UploadGroupResult', N'U') IS NOT NULL
                                    AND OBJECT_ID(N'TRUpload.dbo.UploadedBilling', N'U') IS NOT NULL
                                    AND OBJECT_ID(N'TRUpload.dbo.UploadFailedBilling', N'U') IS NOT NULL
                                    THEN 1
                                    ELSE 0
                                END
                                """;

                Integer tablesExist = sqlServerJdbcTemplate.queryForObject(
                                sql,
                                Integer.class);

                if (!Integer.valueOf(1).equals(tablesExist)) {
                        throw new IllegalStateException(
                                        "TRUpload database is missing one or more required tables: "
                                                        + "UploadBatch, UploadGroupResult, UploadedBilling, "
                                                        + "UploadFailedBilling. Please create the tables before "
                                                        + "starting the application.");
                }

                System.out.println(
                                "TRUpload database verification successful: "
                                                + "all four upload tables exist.");
        }

        public UploadResponse upload(
                        UploadRequest request,
                        String uploadedBy,
                        String userRole) {

                if (request == null || request.selections() == null
                                || request.selections().isEmpty()) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Select at least one billing group to upload.");
                }

                if (uploadedBy == null || uploadedBy.isBlank()) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Authenticated username is required.");
                }

                if (!"ADMIN".equals(userRole) && !"USER".equals(userRole)) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Invalid user role.");
                }

                LinkedHashSet<UploadSelection> selections = new LinkedHashSet<>(request.selections());

                for (UploadSelection selection : selections) {
                        validateSelection(selection);
                }

                UUID batchId = UUID.randomUUID();

                sqlServerJdbcTemplate.update("""
                                INSERT INTO [TRUpload].[dbo].[UploadBatch]
                                    (UploadBatchId, UploadedBy, UserRole, Status, RequestedCount)
                                VALUES (?, ?, ?, 'IN_PROGRESS', ?)
                                """,
                                batchId, uploadedBy, userRole, selections.size());

                List<UploadRecordResult> results = new ArrayList<>();
                LinkedHashSet<String> shuttleWarnings = new LinkedHashSet<>();

                int uploadedCount = 0;
                int failedCount = 0;
                int warningCount = 0;
                long activeGroupResultId = 0;

                try {
                        for (UploadSelection selection : selections) {

                                String databaseName = selection.databaseName().trim();
                                String routeGroupId = selection.routeGroupId().trim();

                                boolean shuttle = routeGroupId.toUpperCase(Locale.ROOT).endsWith("S");

                                if (shuttle) {
                                        shuttleWarnings.add(
                                                        databaseName + " / " + routeGroupId
                                                                        + " is marked as a shuttle delivery (warehouse destination).");
                                        warningCount++;
                                }

                                // Create one group-result record for this selected group.
                                long groupResultId = sqlServerJdbcTemplate.queryForObject("""
                                                INSERT INTO [TRUpload].[dbo].[UploadGroupResult]
                                                    (UploadBatchId, DatabaseName, RouteGroupId, Status)
                                                OUTPUT INSERTED.UploadGroupResultId
                                                VALUES (?, ?, ?, 'IN_PROGRESS')
                                                """,
                                                Long.class, batchId, databaseName, routeGroupId);
                                activeGroupResultId = groupResultId;

                                if (groupExistsInUploadedTable(databaseName, routeGroupId)) {
                                        String reason = "This billing group has already been uploaded.";

                                        saveFailure(
                                                        databaseName, routeGroupId, null, null,
                                                        reason, uploadedBy, groupResultId,
                                                        null, true, false, false, shuttle);

                                        sqlServerJdbcTemplate.update("""
                                                        UPDATE [TRUpload].[dbo].[UploadGroupResult]
                                                        SET Status = 'FAILED',
                                                                CompletedAt = SYSUTCDATETIME(),
                                                                RouteCount = 0,
                                                                UploadedRouteCount = 0,
                                                                FailedRouteCount = 0,
                                                                SummaryMessage = ?
                                                        WHERE UploadGroupResultId = ?
                                                        """,
                                                        reason, groupResultId);

                                        results.add(new UploadRecordResult(
                                                        databaseName, routeGroupId, null, null,
                                                        "FAILED", List.of(reason), shuttle));

                                        failedCount++;
                                        activeGroupResultId = 0;
                                        continue;
                                }

                                List<RouteRecord> routes = findRoutes(databaseName, routeGroupId);

                                // No routes were found: record a group-level failure.
                                if (routes.isEmpty()) {
                                        String reason = "No eligible route records were found for this billing group.";

                                        saveFailure(
                                                        databaseName, routeGroupId, null, null,
                                                        reason, uploadedBy, groupResultId,
                                                        null, false, false, false, shuttle);

                                        sqlServerJdbcTemplate.update("""
                                                        UPDATE [TRUpload].[dbo].[UploadGroupResult]
                                                        SET Status = 'FAILED',
                                                            CompletedAt = SYSUTCDATETIME(),
                                                            RouteCount = 0,
                                                            UploadedRouteCount = 0,
                                                            FailedRouteCount = 0,
                                                            SummaryMessage = ?
                                                        WHERE UploadGroupResultId = ?
                                                        """,
                                                        reason, groupResultId);

                                        results.add(new UploadRecordResult(
                                                        databaseName, routeGroupId, null, null,
                                                        "FAILED", List.of(reason), shuttle));

                                        failedCount++;

                                        databaseInstanceService.invalidateRouteCache(databaseName);
                                        activeGroupResultId = 0;
                                        continue;
                                }

                                // Validate EVERY route before inserting any route from this group.
                                List<RouteCheck> checks = new ArrayList<>();

                                for (RouteRecord route : routes) {
                                        List<String> reasons = new ArrayList<>(
                                                        validateRoute(route, routeGroupId));

                                        boolean duplicate = route.dispatchDate() != null
                                                        && route.routeId() != null
                                                        && existsInUploadedTable(new BillingDuplicateKey(
                                                                        databaseName, route.routeId(), routeGroupId,
                                                                        route.dispatchDate()));

                                        if (duplicate) {
                                                reasons.add(
                                                                "This RouteId, RouteGroupId, and dispatch date "
                                                                                + "have already been uploaded.");
                                        }

                                        boolean dateGapWarning = isDateGapWarning(route, routeGroupId);

                                        checks.add(new RouteCheck(
                                                        route, List.copyOf(reasons),
                                                        duplicate, dateGapWarning));
                                }

                                boolean groupHasBlockingError = checks.stream()
                                                .anyMatch(check -> !check.reasons().isEmpty());

                                if (groupHasBlockingError) {

                                        String triggeringRoute = checks.stream()
                                                        .filter(check -> !check.reasons().isEmpty())
                                                        .map(check -> check.route().routeId())
                                                        .filter(java.util.Objects::nonNull)
                                                        .findFirst()
                                                        .orElse("an unidentified route");

                                        int groupFailedCount = 0;

                                        for (RouteCheck check : checks) {
                                                RouteRecord route = check.route();
                                                List<String> reasons = check.reasons();

                                                if (reasons.isEmpty()) {
                                                        reasons = List.of(
                                                                        "Group rejected because route "
                                                                                        + triggeringRoute
                                                                                        + " failed validation.");
                                                }

                                                String reasonText = String.join("; ", reasons);

                                                boolean weekdayValid = !reasons.stream()
                                                                .anyMatch(reason -> reason.contains("weekday")
                                                                                || reason.contains("weekday digit"));

                                                boolean pastDate = route.dispatchDate() != null
                                                                && route.dispatchDate().isBefore(LocalDate.now());

                                                boolean duplicate = check.duplicate();

                                                saveFailure(
                                                                databaseName, routeGroupId,
                                                                route.routeId(), route.dispatchDate(),
                                                                reasonText, uploadedBy, groupResultId,
                                                                weekdayValid, duplicate, pastDate,
                                                                check.dateGapWarning(), shuttle);

                                                results.add(new UploadRecordResult(
                                                                databaseName, routeGroupId, route.routeId(),
                                                                route.dispatchDate(), "FAILED",
                                                                List.copyOf(reasons), shuttle));

                                                groupFailedCount++;

                                                if (check.dateGapWarning()) {
                                                        warningCount++;
                                                }
                                        }

                                        failedCount += groupFailedCount;

                                        sqlServerJdbcTemplate.update("""
                                                        UPDATE [TRUpload].[dbo].[UploadGroupResult]
                                                        SET Status = 'FAILED',
                                                            CompletedAt = SYSUTCDATETIME(),
                                                            RouteCount = ?,
                                                            UploadedRouteCount = 0,
                                                            FailedRouteCount = ?,
                                                            SummaryMessage = ?
                                                        WHERE UploadGroupResultId = ?
                                                        """,
                                                        routes.size(), groupFailedCount,
                                                        "Group rejected because one or more routes failed validation.",
                                                        groupResultId);

                                        databaseInstanceService.invalidateRouteCache(databaseName);
                                        activeGroupResultId = 0;
                                        continue;
                                }

                                try {
                                        // Convert the already-validated routes into the input
                                        // expected by the transactional service.
                                        List<BillingGroupTransactionService.RouteToUpload> routesToUpload = checks
                                                        .stream()
                                                        .map(check -> new BillingGroupTransactionService.RouteToUpload(
                                                                        check.route().routeId(),
                                                                        check.route().dispatchDate(),
                                                                        check.dateGapWarning()))
                                                        .toList();

                                        // This call crosses the Spring service boundary.
                                        // All billing inserts and the group's SUCCESS status
                                        // are committed together, or rolled back together.
                                        BillingGroupTransactionService.GroupInsertResult insertResult = billingGroupTransactionService
                                                        .insertSuccessfulGroup(
                                                                        groupResultId,
                                                                        databaseName,
                                                                        routeGroupId,
                                                                        uploadedBy,
                                                                        shuttle,
                                                                        routesToUpload);

                                        // Add response records only after the transactional method
                                        // returns successfully.
                                        for (RouteCheck check : checks) {
                                                RouteRecord route = check.route();

                                                List<String> warnings = new ArrayList<>();

                                                if (shuttle) {
                                                        warnings.add("Shuttle delivery (warehouse destination).");
                                                }

                                                if (check.dateGapWarning()) {
                                                        warnings.add(
                                                                        "Dispatch date is more than 3 days after "
                                                                                        + "the MMDD date in RouteGroupId.");
                                                }

                                                results.add(new UploadRecordResult(
                                                                databaseName,
                                                                withWaveSuffix(routeGroupId),
                                                                route.routeId(),
                                                                route.dispatchDate(),
                                                                "UPLOADED",
                                                                List.copyOf(warnings),
                                                                shuttle));
                                        }

                                        uploadedCount += insertResult.uploadedCount();
                                        warningCount += insertResult.warningCount();

                                } catch (DuplicateKeyException duplicateKey) {

                                        // The transactional service has thrown the exception.
                                        // Spring rolls back its transaction before control reaches here.
                                        int groupFailedCount = 0;

                                        for (RouteCheck check : checks) {
                                                RouteRecord route = check.route();

                                                boolean duplicate = route.dispatchDate() != null
                                                                && route.routeId() != null
                                                                && existsInUploadedTable(new BillingDuplicateKey(
                                                                                databaseName, route.routeId(),
                                                                                routeGroupId, route.dispatchDate()));

                                                String reason = duplicate
                                                                ? "This RouteId, RouteGroupId, and dispatch date "
                                                                                + "have already been uploaded."
                                                                : "Group rejected because another route could not "
                                                                                + "be inserted due to a duplicate conflict.";

                                                saveFailure(
                                                                databaseName,
                                                                routeGroupId,
                                                                route.routeId(),
                                                                route.dispatchDate(),
                                                                reason,
                                                                uploadedBy,
                                                                groupResultId,
                                                                true,
                                                                duplicate,
                                                                false,
                                                                check.dateGapWarning(),
                                                                shuttle);

                                                results.add(new UploadRecordResult(
                                                                databaseName,
                                                                routeGroupId,
                                                                route.routeId(),
                                                                route.dispatchDate(),
                                                                "FAILED",
                                                                List.of(reason),
                                                                shuttle));

                                                groupFailedCount++;
                                        }

                                        failedCount += groupFailedCount;

                                        // This update is outside the rolled-back group transaction.
                                        sqlServerJdbcTemplate.update("""
                                                        UPDATE [TRUpload].[dbo].[UploadGroupResult]
                                                        SET Status = 'FAILED',
                                                            CompletedAt = SYSUTCDATETIME(),
                                                            RouteCount = ?,
                                                            UploadedRouteCount = 0,
                                                            FailedRouteCount = ?,
                                                            SummaryMessage = ?
                                                        WHERE UploadGroupResultId = ?
                                                        """,
                                                        routes.size(),
                                                        groupFailedCount,
                                                        "Group rejected due to a duplicate insert conflict.",
                                                        groupResultId);
                                }

                                activeGroupResultId = 0;
                                databaseInstanceService.invalidateRouteCache(databaseName);
                        }
                } catch (RuntimeException unexpectedFailure) {
                        String failureSummary = "Unexpected upload processing error: "
                                        + unexpectedFailure.getClass().getSimpleName()
                                        + (unexpectedFailure.getMessage() == null ? ""
                                                        : ": " + unexpectedFailure.getMessage());

                        if (activeGroupResultId != 0) {
                                try {
                                        sqlServerJdbcTemplate.update("""
                                                        UPDATE [TRUpload].[dbo].[UploadGroupResult]
                                                        SET Status = 'FAILED',
                                                                CompletedAt = SYSUTCDATETIME(),
                                                                SummaryMessage = ?
                                                        WHERE UploadGroupResultId = ?
                                                        """, failureSummary, activeGroupResultId);
                                } catch (RuntimeException statusFailure) {
                                        unexpectedFailure.addSuppressed(statusFailure);
                                }
                        }

                        try {
                                sqlServerJdbcTemplate.update("""
                                                UPDATE [TRUpload].[dbo].[UploadBatch]
                                                SET Status = 'FAILED',
                                                        CompletedAt = SYSUTCDATETIME(),
                                                        UploadedCount = ?,
                                                        FailedCount = ?,
                                                        WarningCount = ?
                                                WHERE UploadBatchId = ?
                                                """, uploadedCount, failedCount, warningCount, batchId);
                        } catch (RuntimeException statusFailure) {
                                unexpectedFailure.addSuppressed(statusFailure);
                        }

                        throw unexpectedFailure;
                }

                String batchStatus;

                if (failedCount == 0) {
                        batchStatus = warningCount > 0
                                        ? "COMPLETED_WITH_WARNINGS"
                                        : "COMPLETED";
                } else if (uploadedCount > 0) {
                        batchStatus = "PARTIALLY_FAILED";
                } else {
                        batchStatus = "FAILED";
                }

                sqlServerJdbcTemplate.update("""
                                UPDATE [TRUpload].[dbo].[UploadBatch]
                                SET Status = ?,
                                    CompletedAt = SYSUTCDATETIME(),
                                    UploadedCount = ?,
                                    FailedCount = ?,
                                    WarningCount = ?
                                WHERE UploadBatchId = ?
                                """,
                                batchStatus, uploadedCount, failedCount,
                                warningCount, batchId);

                return new UploadResponse(
                                batchId, uploadedCount, failedCount,
                                List.copyOf(shuttleWarnings), List.copyOf(results));
        }

        public List<FailedUploadRecord> findRecentFailures() {
                return findRecentFailures(null, true);
        }

        public List<FailedUploadRecord> findRecentFailures(String username, boolean admin) {
                return sqlServerJdbcTemplate.query("""
                                SELECT TOP (1000)
                                    UploadFailedBillingId, DatabaseName, RouteGroupId, RouteId,
                                    DispatchDate, FailureReason, AttemptedAt, AttemptedBy
                                FROM [TRUpload].[dbo].[UploadFailedBilling]
                                                                WHERE (? = 1 OR AttemptedBy = ?)
                                ORDER BY AttemptedAt DESC, UploadFailedBillingId DESC
                                                                """, (rs, rowNum) -> {
                        java.sql.Date dispatchDate = rs.getDate("DispatchDate");
                        Timestamp attemptedAt = rs.getTimestamp("AttemptedAt");
                        return new FailedUploadRecord(
                                        rs.getLong("UploadFailedBillingId"),
                                        rs.getString("DatabaseName"),
                                        rs.getString("RouteGroupId"),
                                        rs.getString("RouteId"),
                                        dispatchDate == null ? null : dispatchDate.toLocalDate(),
                                        rs.getString("FailureReason"),
                                        attemptedAt == null ? null : attemptedAt.toLocalDateTime(),
                                        rs.getString("AttemptedBy"));
                }, admin ? 1 : 0, username);
        }

        public List<SuccessfulUploadRecord> findRecentSuccessfulUploads() {
                return findRecentSuccessfulUploads(null, true);
        }

        public List<SuccessfulUploadRecord> findRecentSuccessfulUploads(String username, boolean admin) {
                return sqlServerJdbcTemplate.query("""
                                SELECT TOP (1000)
                                    UploadedBillingId, DatabaseName, RouteGroupId, RouteId,
                                    DispatchDate, UploadedAt, UploadedBy, Warnings
                                FROM [TRUpload].[dbo].[UploadedBilling]
                                WHERE (? = 1 OR UploadedBy = ?)
                                ORDER BY UploadedAt DESC, UploadedBillingId DESC
                                """, (rs, rowNum) -> {
                        java.sql.Date dispatchDate = rs.getDate("DispatchDate");
                        Timestamp uploadedAt = rs.getTimestamp("UploadedAt");
                        return new SuccessfulUploadRecord(
                                        rs.getLong("UploadedBillingId"),
                                        rs.getString("DatabaseName"),
                                        rs.getString("RouteGroupId"),
                                        rs.getString("RouteId"),
                                        dispatchDate == null ? null : dispatchDate.toLocalDate(),
                                        uploadedAt == null ? null : uploadedAt.toLocalDateTime(),
                                        rs.getString("UploadedBy"),
                                        rs.getString("Warnings"));
                }, admin ? 1 : 0, username);
        }

        private List<RouteRecord> findRoutes(String databaseName, String routeGroupId) {
                String query = String.format("""
                                SELECT
                                    r.ID AS RouteID,
                                    r.GroupID AS RouteGroupID,
                                    CAST(CAST(r.DispatchDateTime AS FLOAT) AS DATETIME) AS DispatchDateTime
                                FROM [%s].[dbo].[Route] r
                                WHERE r.GroupID = ?
                                  AND r.GroupID IS NOT NULL
                                  AND LOWER(RTRIM(r.GroupID)) NOT LIKE '%%wave'
                                  AND r.GroupID LIKE '[0-9][0-9][0-9][0-9][A-Za-z][0-9][0-9]%%'
                                ORDER BY r.ID, r.DispatchDateTime
                                """, databaseName);

                return sqlServerJdbcTemplate.query(query, (rs, rowNum) -> {
                        Timestamp dispatchTimestamp = rs.getTimestamp("DispatchDateTime");
                        LocalDate dispatchDate = dispatchTimestamp == null
                                        ? null
                                        : dispatchTimestamp.toLocalDateTime().toLocalDate();
                        return new RouteRecord(rs.getString("RouteID"), dispatchDate);
                }, routeGroupId);
        }

        private List<String> validateRoute(RouteRecord route, String routeGroupId) {
                return routeValidator.validate(route.routeId(), route.dispatchDate(), routeGroupId);
        }

        private LocalDate nearestGroupDate(String routeGroupId, LocalDate dispatchDate) {
                return routeValidator.nearestGroupDate(routeGroupId, dispatchDate);
        }

        private String withWaveSuffix(String routeGroupId) {
                return routeGroupId.toLowerCase(Locale.ROOT).endsWith("wave")
                                ? routeGroupId
                                : routeGroupId + "wave";
        }

        private boolean groupExistsInUploadedTable(String databaseName, String routeGroupId) {
                String baseRouteGroupId = routeGroupId.replaceFirst("(?i)wave$", "");
                Integer exists = sqlServerJdbcTemplate.queryForObject(
                                """
                                                SELECT CASE WHEN EXISTS (
                                                    SELECT 1
                                                    FROM [TRUpload].[dbo].[UploadedBilling]
                                                    WHERE DatabaseName = ?
                                                      AND LOWER(RTRIM(RouteGroupId)) IN (LOWER(?), LOWER(?))
                                                ) THEN 1 ELSE 0 END
                                                """,
                                Integer.class,
                                databaseName, baseRouteGroupId, baseRouteGroupId + "wave");
                return Integer.valueOf(1).equals(exists);
        }

        private boolean existsInUploadedTable(BillingDuplicateKey duplicateKey) {
                Integer count = sqlServerJdbcTemplate.queryForObject(
                                """
                                                SELECT COUNT(*)
                                                FROM [TRUpload].[dbo].[UploadedBilling]
                                                                                                WHERE DatabaseName = ?
                                                                                                        AND RouteId = ?
                                                                                                        AND (RTRIM(RouteGroupId) = ?
                                                                                                                         OR LOWER(RTRIM(RouteGroupId)) = LOWER(CONCAT(?, 'wave')))
                                                                                                        AND DispatchDate = ?
                                                                """,
                                Integer.class,
                                duplicateKey.databaseName(), duplicateKey.routeId(),
                                duplicateKey.routeGroupId(), duplicateKey.routeGroupId(), duplicateKey.dispatchDate());
                return count != null && count > 0;
        }

        private void saveFailure(
                        String databaseName,
                        String routeGroupId,
                        String routeId,
                        LocalDate dispatchDate,
                        String reason,
                        String attemptedBy,
                        long groupResultId,
                        Boolean weekdayValid,
                        boolean duplicate,
                        boolean pastDate,
                        boolean dateGapWarning,
                        boolean shuttle) {

                sqlServerJdbcTemplate.update("""
                                INSERT INTO [TRUpload].[dbo].[UploadFailedBilling]
                                    (UploadGroupResultId, DatabaseName, RouteId,
                                    RouteGroupId, DispatchDate, FailureReason,
                                    AttemptedBy, IsWeekdayValid, IsDuplicate,
                                    IsPastDate, IsDateGapWarning, IsShuttle)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                                groupResultId,
                                databaseName,
                                routeId,
                                routeGroupId,
                                dispatchDate,
                                reason,
                                attemptedBy,
                                weekdayValid,
                                duplicate,
                                pastDate,
                                dateGapWarning,
                                shuttle);
        }

        private void validateSelection(UploadSelection selection) {
                if (selection == null || selection.databaseName() == null || selection.routeGroupId() == null
                                || !SAFE_DATABASE_NAME.matcher(selection.databaseName().trim()).matches()
                                || selection.routeGroupId().isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "A selected database or billing group is invalid.");
                }
        }

        private boolean isDateGapWarning(
                        RouteRecord route,
                        String routeGroupId) {

                if (route.dispatchDate() == null) {
                        return false;
                }

                LocalDate groupDate = nearestGroupDate(routeGroupId, route.dispatchDate());

                return groupDate != null
                                && ChronoUnit.DAYS.between(
                                                groupDate, route.dispatchDate()) > 3;
        }

        record RouteRecord(String routeId, LocalDate dispatchDate) {
        }

        record BillingDuplicateKey(
                        String databaseName,
                        String routeId,
                        String routeGroupId,
                        LocalDate dispatchDate) {
        }

        private record RouteCheck(
                        RouteRecord route,
                        List<String> reasons,
                        boolean duplicate,
                        boolean dateGapWarning) {
        }
}
