package com.trupload.api.access;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AccountAccessService {
    private final JdbcTemplate jdbcTemplate;

    public AccountAccessService(@Qualifier("sqlServerJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WarehouseResponse> findWarehouses() {
        return jdbcTemplate.query("""
                  SELECT WarehouseID, Facility, [database], UploadCode, Division
                FROM [TRUpload].[dbo].[Warehouses]
                ORDER BY Division, Facility, [database]
                """, (rs, rowNum) -> new WarehouseResponse(
                rs.getLong("WarehouseID"), rs.getString("Facility"), rs.getString("database"),
                rs.getString("UploadCode"), rs.getString("Division")));
    }

    public List<WarehouseResponse> findMyWarehouses(String email) {
        return jdbcTemplate.query("""
                  SELECT W.WarehouseID, W.Facility, W.[database], W.UploadCode, W.Division
                FROM [TRUpload].[dbo].[UserWarehouses] UW
                JOIN [TRUpload].[dbo].[Warehouses] W ON W.WarehouseID = UW.WarehouseID
                JOIN [TRUpload].[dbo].[Users] U ON U.UserID = UW.UserID
                WHERE U.Email = ? AND U.Status = 'ACTIVE' AND UW.IsActive = 1
                ORDER BY W.Division, W.Facility
                """, (rs, rowNum) -> new WarehouseResponse(
                rs.getLong("WarehouseID"), rs.getString("Facility"), rs.getString("database"),
                rs.getString("UploadCode"), rs.getString("Division")), email);
    }

    public List<AccountRequestResponse> findAccountRequests() {
        List<AccountRequestResponse> requests = jdbcTemplate.query("""
                SELECT AR.AccountRequestID, U.UserID, U.FirstName, U.LastName, U.Username,
                       U.Email, U.Status AS AccountStatus, AR.RequestStatus, AR.RequestedAt
                FROM [TRUpload].[dbo].[AccountRequests] AR
                JOIN [TRUpload].[dbo].[Users] U ON U.UserID = AR.UserID
                ORDER BY CASE WHEN AR.RequestStatus = 'PENDING' THEN 0 ELSE 1 END,
                         AR.RequestedAt DESC
                """, (rs, rowNum) -> new AccountRequestResponse(
                rs.getLong("AccountRequestID"), rs.getLong("UserID"), rs.getString("FirstName"),
                rs.getString("LastName"), rs.getString("Username"), rs.getString("Email"),
                rs.getString("AccountStatus"), rs.getString("RequestStatus"),
                localDateTime(rs, "RequestedAt"), new ArrayList<>()));

        for (int index = 0; index < requests.size(); index++) {
            AccountRequestResponse request = requests.get(index);
            List<AccountRequestResponse.WarehouseRequest> warehouses = jdbcTemplate.query("""
                    SELECT ARW.AccountRequestWarehouseID, W.WarehouseID, W.Facility, W.[database],
                           W.UploadCode, W.Division, ARW.RequestStatus, ARW.ReviewComments
                    FROM [TRUpload].[dbo].[AccountRequestWarehouses] ARW
                    JOIN [TRUpload].[dbo].[Warehouses] W ON W.WarehouseID = ARW.WarehouseID
                    WHERE ARW.AccountRequestID = ?
                    ORDER BY W.Division, W.Facility
                    """, (rs, rowNum) -> new AccountRequestResponse.WarehouseRequest(
                    rs.getLong("AccountRequestWarehouseID"), rs.getLong("WarehouseID"),
                    rs.getString("Facility"), rs.getString("database"), rs.getString("UploadCode"),
                    rs.getString("Division"), rs.getString("RequestStatus"), rs.getString("ReviewComments")),
                    request.accountRequestId());
            requests.set(index, new AccountRequestResponse(
                    request.accountRequestId(), request.userId(), request.firstName(), request.lastName(),
                    request.username(), request.email(), request.accountStatus(), request.requestStatus(),
                    request.requestedAt(), warehouses));
        }
        return requests;
    }

    @Transactional(transactionManager = "sqlServerTransactionManager")
    public void decideAccount(long accountRequestId, String decision, String comments, String actorEmail) {
        String status = normalizeDecision(decision);
        long actorId = userId(actorEmail);
        long userId = jdbcTemplate.queryForObject("""
                SELECT UserID FROM [TRUpload].[dbo].[AccountRequests] WHERE AccountRequestID = ?
                """, Long.class, accountRequestId);
        assertNotSelf(actorId, userId);
        int updated = jdbcTemplate.update("""
                UPDATE [TRUpload].[dbo].[AccountRequests]
                SET RequestStatus = ?, ReviewedBy = ?, ReviewedAt = SYSUTCDATETIME(), ReviewComments = ?
                WHERE AccountRequestID = ? AND RequestStatus = 'PENDING'
                """, status, actorId, comments, accountRequestId);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account request is no longer pending.");
        }
        jdbcTemplate.update("""
                UPDATE [TRUpload].[dbo].[Users] SET Status = ?, UpdatedAt = SYSUTCDATETIME()
                WHERE UserID = ?
                """, "APPROVED".equals(status) ? "ACTIVE" : "REJECTED", userId);
        if ("APPROVED".equals(status)) {
            jdbcTemplate.update("""
                    INSERT INTO [TRUpload].[dbo].[UserWarehouses] (UserID, WarehouseID, AssignedBy)
                    SELECT ?, ARW.WarehouseID, ?
                    FROM [TRUpload].[dbo].[AccountRequestWarehouses] ARW
                    WHERE ARW.AccountRequestID = ? AND ARW.RequestStatus = 'APPROVED'
                      AND NOT EXISTS (
                          SELECT 1 FROM [TRUpload].[dbo].[UserWarehouses] UW
                          WHERE UW.UserID = ? AND UW.WarehouseID = ARW.WarehouseID)
                    """, userId, actorId, accountRequestId, userId);
        }
        writeAudit(actorId, "ACCOUNT_" + status, "AccountRequest", accountRequestId,
                comments == null ? "Account request " + status.toLowerCase() : comments);
    }

    @Transactional(transactionManager = "sqlServerTransactionManager")
    public void decideAccountWarehouse(long requestWarehouseId, String decision, String comments, String actorEmail) {
        String status = normalizeDecision(decision);
        long actorId = userId(actorEmail);
        Long targetUserId = jdbcTemplate.queryForObject("""
                SELECT AR.UserID
                FROM [TRUpload].[dbo].[AccountRequestWarehouses] ARW
                JOIN [TRUpload].[dbo].[AccountRequests] AR ON AR.AccountRequestID = ARW.AccountRequestID
                WHERE ARW.AccountRequestWarehouseID = ?
                """, Long.class, requestWarehouseId);
        assertNotSelf(actorId, targetUserId);
        int updated = jdbcTemplate.update("""
                    UPDATE [TRUpload].[dbo].[AccountRequestWarehouses]
                    SET RequestStatus = ?, ReviewedBy = ?, ReviewedAt = SYSUTCDATETIME(), ReviewComments = ?
                WHERE AccountRequestWarehouseID = ? AND RequestStatus = 'PENDING'
                    """, status, actorId, comments, requestWarehouseId);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This warehouse request is no longer pending.");
        }
        if ("APPROVED".equals(status)) {
            jdbcTemplate.update("""
                    INSERT INTO [TRUpload].[dbo].[UserWarehouses] (UserID, WarehouseID, AssignedBy)
                    SELECT AR.UserID, ARW.WarehouseID, ?
                    FROM [TRUpload].[dbo].[AccountRequestWarehouses] ARW
                    JOIN [TRUpload].[dbo].[AccountRequests] AR ON AR.AccountRequestID = ARW.AccountRequestID
                    JOIN [TRUpload].[dbo].[Users] U ON U.UserID = AR.UserID
                    WHERE ARW.AccountRequestWarehouseID = ? AND U.Status = 'ACTIVE'
                      AND NOT EXISTS (
                          SELECT 1 FROM [TRUpload].[dbo].[UserWarehouses] UW
                          WHERE UW.UserID = AR.UserID AND UW.WarehouseID = ARW.WarehouseID)
                    """, actorId, requestWarehouseId);
        }
        writeAudit(actorId, "ACCOUNT_WAREHOUSE_" + status, "AccountRequestWarehouse",
                requestWarehouseId, comments);
    }

    public List<AccountRequestResponse> findMyAccountRequests(String email) {
        Set<Long> userIds = Set.of(userId(email));
        return findAccountRequests().stream().filter(request -> userIds.contains(request.userId())).toList();
    }

    public List<WarehouseAccessResponse> findWarehouseAccessRequests(String email, boolean admin) {
        String sql = """
                SELECT WAR.WarehouseAccessRequestID, U.UserID, U.Username, U.Email,
                       WAR.RequestType, WAR.CurrentWarehouseID, CW.Facility AS CurrentFacility,
                       WAR.RequestedWarehouseID, RW.Facility AS RequestedFacility,
                       WAR.RequestStatus, WAR.RequestedAt, WAR.ReviewComments
                FROM [TRUpload].[dbo].[WarehouseAccessRequests] WAR
                JOIN [TRUpload].[dbo].[Users] U ON U.UserID = WAR.UserID
                LEFT JOIN [TRUpload].[dbo].[Warehouses] CW ON CW.WarehouseID = WAR.CurrentWarehouseID
                LEFT JOIN [TRUpload].[dbo].[Warehouses] RW ON RW.WarehouseID = WAR.RequestedWarehouseID
                WHERE (? = 1 OR U.Email = ?)
                ORDER BY CASE WHEN WAR.RequestStatus = 'PENDING' THEN 0 ELSE 1 END,
                         WAR.RequestedAt DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new WarehouseAccessResponse(
                rs.getLong("WarehouseAccessRequestID"), rs.getLong("UserID"), rs.getString("Username"),
                rs.getString("Email"), rs.getString("RequestType"), nullableLong(rs, "CurrentWarehouseID"),
                rs.getString("CurrentFacility"), nullableLong(rs, "RequestedWarehouseID"),
                rs.getString("RequestedFacility"), rs.getString("RequestStatus"),
                localDateTime(rs, "RequestedAt"), rs.getString("ReviewComments")),
                admin ? 1 : 0, email);
    }

    public List<AdminUserResponse> findUsers() {
        List<AdminUserResponse> users = jdbcTemplate.query("""
                SELECT U.UserID, U.Username, U.Email, U.FirstName, U.LastName, U.Status,
                       COALESCE(R.RoleName, 'USER') AS RoleName
                FROM [TRUpload].[dbo].[Users] U
                OUTER APPLY (
                    SELECT TOP (1) RoleName FROM [TRUpload].[dbo].[UserRoles] UR
                    JOIN [TRUpload].[dbo].[Roles] R ON R.RoleID = UR.RoleID
                    WHERE UR.UserID = U.UserID
                    ORDER BY CASE WHEN R.RoleName = 'ADMIN' THEN 0 ELSE 1 END
                ) R
                ORDER BY U.CreatedAt DESC
                """, (rs, rowNum) -> new AdminUserResponse(
                rs.getLong("UserID"), rs.getString("Username"), rs.getString("Email"),
                rs.getString("FirstName"), rs.getString("LastName"), rs.getString("Status"),
                rs.getString("RoleName"), findUserWarehouses(rs.getLong("UserID"))));
        return users;
    }

    public List<AuditLogResponse> findAuditLogs() {
        return jdbcTemplate.query("""
                SELECT TOP (1000) A.AuditLogID, A.ActorUserID, U.Email AS ActorEmail,
                       A.ActionType, A.EntityType, A.EntityID, A.Description, A.CreatedAt
                FROM [TRUpload].[dbo].[AuditLogs] A
                LEFT JOIN [TRUpload].[dbo].[Users] U ON U.UserID = A.ActorUserID
                ORDER BY A.CreatedAt DESC, A.AuditLogID DESC
                """, (rs, rowNum) -> new AuditLogResponse(
                rs.getLong("AuditLogID"), nullableLong(rs, "ActorUserID"), rs.getString("ActorEmail"),
                rs.getString("ActionType"), rs.getString("EntityType"), nullableLong(rs, "EntityID"),
                rs.getString("Description"), localDateTime(rs, "CreatedAt")));
    }

    @Transactional(transactionManager = "sqlServerTransactionManager")
    public void updateUserStatus(long targetUserId, String requestedStatus, String actorEmail) {
        String status = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase();
        if (!Set.of("ACTIVE", "DISABLED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or DISABLED.");
        }
        long actorId = userId(actorEmail);
        assertNotSelf(actorId, targetUserId);
        String previousStatus = jdbcTemplate.queryForObject(
                "SELECT Status FROM [TRUpload].[dbo].[Users] WHERE UserID = ?", String.class, targetUserId);
        if ("ACTIVE".equals(status) && ("PENDING".equals(previousStatus) || "REJECTED".equals(previousStatus))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Pending or rejected accounts must be approved through account requests.");
        }
        jdbcTemplate.update("""
                UPDATE [TRUpload].[dbo].[Users] SET Status = ?, UpdatedAt = SYSUTCDATETIME()
                WHERE UserID = ?
                """, status, targetUserId);
        if ("ACTIVE".equals(previousStatus) && "DISABLED".equals(status) && isAdmin(targetUserId)) {
            ensureAnotherActiveAdmin(targetUserId);
        }
        writeAudit(actorId, "ACCOUNT_STATUS_CHANGED", "User", targetUserId, previousStatus + " -> " + status);
    }

    @Transactional(transactionManager = "sqlServerTransactionManager")
    public void updateUserRole(long targetUserId, String requestedRole, String actorEmail) {
        String role = requestedRole == null ? "" : requestedRole.trim().toUpperCase();
        long actorId = userId(actorEmail);
        assertNotSelf(actorId, targetUserId);
        Long roleId = jdbcTemplate.query("""
                SELECT RoleID FROM [TRUpload].[dbo].[Roles] WHERE RoleName = ?
                """, (rs, rowNum) -> rs.getLong(1), role).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is not configured."));
        if (isAdmin(targetUserId) && !"ADMIN".equals(role)) {
            ensureAnotherActiveAdmin(targetUserId);
        }
        jdbcTemplate.update("DELETE FROM [TRUpload].[dbo].[UserRoles] WHERE UserID = ?", targetUserId);
        jdbcTemplate.update("INSERT INTO [TRUpload].[dbo].[UserRoles] (UserID, RoleID) VALUES (?, ?)", targetUserId,
                roleId);
        writeAudit(actorId, "USER_ROLE_CHANGED", "User", targetUserId, "Role set to " + role);
    }

    @Transactional(transactionManager = "sqlServerTransactionManager")
    public void assignWarehouse(long targetUserId, long warehouseId, String actorEmail) {
        long actorId = userId(actorEmail);
        assertNotSelf(actorId, targetUserId);
        if (isAdmin(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Warehouse assignment controls are not available for admin users.");
        }
        if (!warehouseExists(warehouseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse does not exist.");
        }
        Integer requestedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM [TRUpload].[dbo].[AccountRequests] AR
                JOIN [TRUpload].[dbo].[AccountRequestWarehouses] ARW
                  ON ARW.AccountRequestID = AR.AccountRequestID
                WHERE AR.UserID = ? AND ARW.WarehouseID = ?
                """, Integer.class, targetUserId, warehouseId);
        if (requestedCount != null && requestedCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This warehouse was already requested during account creation and must be reviewed in the account request.");
        }
        addAssignment(targetUserId, warehouseId, actorId);
        writeAudit(actorId, "WAREHOUSE_ASSIGNED", "User", targetUserId, "Warehouse " + warehouseId + " assigned");
    }

    @Transactional(transactionManager = "sqlServerTransactionManager")
    public void removeWarehouse(long targetUserId, long warehouseId, String actorEmail) {
        long actorId = userId(actorEmail);
        assertNotSelf(actorId, targetUserId);
        if (isAdmin(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Warehouse assignment controls are not available for admin users.");
        }
        disableAssignment(targetUserId, warehouseId);
        writeAudit(actorId, "WAREHOUSE_REMOVED", "User", targetUserId, "Warehouse " + warehouseId + " removed");
    }

    @Transactional(transactionManager = "sqlServerTransactionManager")
    public void replaceWarehouse(long targetUserId, long oldWarehouseId, long newWarehouseId, String actorEmail) {
        long actorId = userId(actorEmail);
        assertNotSelf(actorId, targetUserId);
        if (isAdmin(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Warehouse assignment controls are not available for admin users.");
        }
        if (oldWarehouseId == newWarehouseId || !warehouseExists(newWarehouseId)
                || !hasWarehouse(targetUserId, oldWarehouseId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse replacement is invalid.");
        }
        Integer requestedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM [TRUpload].[dbo].[AccountRequests] AR
                JOIN [TRUpload].[dbo].[AccountRequestWarehouses] ARW
                  ON ARW.AccountRequestID = AR.AccountRequestID
                WHERE AR.UserID = ? AND ARW.WarehouseID = ?
                """, Integer.class, targetUserId, newWarehouseId);
        if (requestedCount != null && requestedCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This warehouse was already requested during account creation and must be reviewed in the account request.");
        }
        disableAssignment(targetUserId, oldWarehouseId);
        addAssignment(targetUserId, newWarehouseId, actorId);
        writeAudit(actorId, "WAREHOUSE_REPLACED", "User", targetUserId,
                oldWarehouseId + " -> " + newWarehouseId);
    }

    @Transactional(transactionManager = "sqlServerTransactionManager")
    public void decideWarehouseAccessRequest(
            long requestId, String decision, String comments, String actorEmail) {
        String status = normalizeDecision(decision);
        long actorId = userId(actorEmail);
        List<WarehouseAccessResponse> rows = jdbcTemplate.query("""
                SELECT WAR.WarehouseAccessRequestID, U.UserID, U.Username, U.Email,
                       WAR.RequestType, WAR.CurrentWarehouseID, CW.Facility AS CurrentFacility,
                       WAR.RequestedWarehouseID, RW.Facility AS RequestedFacility,
                       WAR.RequestStatus, WAR.RequestedAt, WAR.ReviewComments
                FROM [TRUpload].[dbo].[WarehouseAccessRequests] WAR
                JOIN [TRUpload].[dbo].[Users] U ON U.UserID = WAR.UserID
                LEFT JOIN [TRUpload].[dbo].[Warehouses] CW ON CW.WarehouseID = WAR.CurrentWarehouseID
                LEFT JOIN [TRUpload].[dbo].[Warehouses] RW ON RW.WarehouseID = WAR.RequestedWarehouseID
                WHERE WAR.WarehouseAccessRequestID = ?
                """, (rs, rowNum) -> new WarehouseAccessResponse(
                rs.getLong("WarehouseAccessRequestID"), rs.getLong("UserID"), rs.getString("Username"),
                rs.getString("Email"), rs.getString("RequestType"), nullableLong(rs, "CurrentWarehouseID"),
                rs.getString("CurrentFacility"), nullableLong(rs, "RequestedWarehouseID"),
                rs.getString("RequestedFacility"), rs.getString("RequestStatus"),
                localDateTime(rs, "RequestedAt"), rs.getString("ReviewComments")), requestId);
        if (rows.isEmpty() || !"PENDING".equals(rows.get(0).requestStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is no longer pending.");
        }
        WarehouseAccessResponse accessRequest = rows.get(0);
        int updated = jdbcTemplate.update("""
                UPDATE [TRUpload].[dbo].[WarehouseAccessRequests]
                SET RequestStatus = ?, ReviewedBy = ?, ReviewedAt = SYSUTCDATETIME(), ReviewComments = ?
                WHERE WarehouseAccessRequestID = ? AND RequestStatus = 'PENDING'
                """, status, actorId, comments, requestId);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This request was reviewed by another administrator.");
        }
        if ("APPROVED".equals(status)) {
            switch (accessRequest.requestType()) {
                case "ADD" -> addAssignment(accessRequest.userId(), accessRequest.requestedWarehouseId(), actorId);
                case "REMOVE" -> disableAssignment(accessRequest.userId(), accessRequest.currentWarehouseId());
                case "REPLACE" -> {
                    disableAssignment(accessRequest.userId(), accessRequest.currentWarehouseId());
                    addAssignment(accessRequest.userId(), accessRequest.requestedWarehouseId(), actorId);
                }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown access request type.");
            }
        }
        writeAudit(actorId, "WAREHOUSE_ACCESS_" + status, "WarehouseAccessRequest", requestId,
                accessRequest.requestType() + (comments == null ? "" : ": " + comments));
    }

    @Transactional(transactionManager = "sqlServerTransactionManager")
    public void createWarehouseAccessRequest(String email, WarehouseAccessRequest request) {
        String type = request.requestType().trim().toUpperCase();
        if (!Set.of("ADD", "REMOVE", "REPLACE").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid warehouse request type.");
        }
        if ("ADD".equals(type) && (request.currentWarehouseId() != null || request.requestedWarehouseId() == null)
                || "REMOVE".equals(type)
                        && (request.currentWarehouseId() == null || request.requestedWarehouseId() != null)
                || "REPLACE".equals(type)
                        && (request.currentWarehouseId() == null || request.requestedWarehouseId() == null
                                || request.currentWarehouseId().equals(request.requestedWarehouseId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse IDs do not match the request type.");
        }
        long userId = userId(email);
        if (request.requestedWarehouseId() != null && !warehouseExists(request.requestedWarehouseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested warehouse does not exist.");
        }
        if (request.currentWarehouseId() != null && !hasWarehouse(userId, request.currentWarehouseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Current warehouse is not assigned to this account.");
        }
        jdbcTemplate.update("""
                INSERT INTO [TRUpload].[dbo].[WarehouseAccessRequests]
                    (UserID, RequestType, CurrentWarehouseID, RequestedWarehouseID, RequestStatus)
                VALUES (?, ?, ?, ?, 'PENDING')
                """, userId, type, request.currentWarehouseId(), request.requestedWarehouseId());
    }

    public Set<String> permittedDatabases(String email) {
        return new LinkedHashSet<>(jdbcTemplate.query("""
                SELECT DISTINCT W.[database]
                FROM [TRUpload].[dbo].[UserWarehouses] UW
                JOIN [TRUpload].[dbo].[Warehouses] W ON W.WarehouseID = UW.WarehouseID
                JOIN [TRUpload].[dbo].[Users] U ON U.UserID = UW.UserID
                WHERE U.Email = ? AND U.Status = 'ACTIVE' AND UW.IsActive = 1
                """, (rs, rowNum) -> rs.getString(1), email));
    }

    public Set<String> permittedFacilityIds(String email) {
        return new LinkedHashSet<>(jdbcTemplate.query("""
                SELECT DISTINCT CONVERT(VARCHAR(20), W.fAC_id)
                FROM [TRUpload].[dbo].[UserWarehouses] UW
                JOIN [TRUpload].[dbo].[Warehouses] W ON W.WarehouseID = UW.WarehouseID
                JOIN [TRUpload].[dbo].[Users] U ON U.UserID = UW.UserID
                WHERE U.Email = ? AND U.Status = 'ACTIVE' AND UW.IsActive = 1
                """, (rs, rowNum) -> rs.getString(1), email));
    }

    public boolean hasGroupAccess(String email, String databaseName, String routeGroupId) {
        if (databaseName == null || !databaseName.matches("[A-Za-z0-9_]+")) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(String.format("""
                SELECT COUNT(*)
                FROM [%s].[dbo].[Orders] O
                JOIN [TRUpload].[dbo].[UserWarehouses] UW ON UW.IsActive = 1
                JOIN [TRUpload].[dbo].[Warehouses] W ON W.WarehouseID = UW.WarehouseID
                JOIN [TRUpload].[dbo].[Users] U ON U.UserID = UW.UserID AND U.Status = 'ACTIVE'
                WHERE U.Email = ? AND O.GroupID = ?
                  AND TRY_CONVERT(INT, O.SourceID) = W.fAC_id
                  AND W.[database] = ?
                """, databaseName), Integer.class, email, routeGroupId, databaseName);
        return count != null && count > 0;
    }

    private boolean warehouseExists(long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM [TRUpload].[dbo].[Warehouses] WHERE WarehouseID = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private boolean hasWarehouse(long userId, long warehouseId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM [TRUpload].[dbo].[UserWarehouses]
                WHERE UserID = ? AND WarehouseID = ? AND IsActive = 1
                """, Integer.class, userId, warehouseId);
        return count != null && count > 0;
    }

    private void addAssignment(long userId, Long warehouseId, long actorId) {
        if (warehouseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested warehouse is missing.");
        }
        jdbcTemplate.update("""
                IF EXISTS (SELECT 1 FROM [TRUpload].[dbo].[UserWarehouses]
                           WHERE UserID = ? AND WarehouseID = ?)
                    UPDATE [TRUpload].[dbo].[UserWarehouses]
                    SET IsActive = 1, AssignedBy = ?, UpdatedAt = SYSUTCDATETIME()
                    WHERE UserID = ? AND WarehouseID = ?
                ELSE
                    INSERT INTO [TRUpload].[dbo].[UserWarehouses] (UserID, WarehouseID, AssignedBy)
                    VALUES (?, ?, ?)
                """, userId, warehouseId, actorId, userId, warehouseId, userId, warehouseId, actorId);
    }

    private List<WarehouseResponse> findUserWarehouses(long userId) {
        return jdbcTemplate.query("""
                  SELECT W.WarehouseID, W.Facility, W.[database], W.UploadCode, W.Division
                FROM [TRUpload].[dbo].[UserWarehouses] UW
                JOIN [TRUpload].[dbo].[Warehouses] W ON W.WarehouseID = UW.WarehouseID
                WHERE UW.UserID = ? AND UW.IsActive = 1
                ORDER BY W.Division, W.Facility
                """, (rs, rowNum) -> new WarehouseResponse(
                rs.getLong("WarehouseID"), rs.getString("Facility"), rs.getString("database"),
                rs.getString("UploadCode"), rs.getString("Division")), userId);
    }

    private boolean isAdmin(long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM [TRUpload].[dbo].[UserRoles] UR
                JOIN [TRUpload].[dbo].[Roles] R ON R.RoleID = UR.RoleID
                WHERE UR.UserID = ? AND R.RoleName = 'ADMIN'
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    private void ensureAnotherActiveAdmin(long excludedUserId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT U.UserID)
                FROM [TRUpload].[dbo].[Users] U
                JOIN [TRUpload].[dbo].[UserRoles] UR ON UR.UserID = U.UserID
                JOIN [TRUpload].[dbo].[Roles] R ON R.RoleID = UR.RoleID
                WHERE U.Status = 'ACTIVE' AND R.RoleName = 'ADMIN' AND U.UserID <> ?
                """, Integer.class, excludedUserId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The last active administrator cannot be removed or disabled.");
        }
    }

    private void assertNotSelf(long actorId, long targetUserId) {
        if (actorId == targetUserId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrators cannot change their own access.");
        }
    }

    private void disableAssignment(long userId, Long warehouseId) {
        if (warehouseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current warehouse is missing.");
        }
        jdbcTemplate.update("""
                UPDATE [TRUpload].[dbo].[UserWarehouses]
                SET IsActive = 0, UpdatedAt = SYSUTCDATETIME()
                WHERE UserID = ? AND WarehouseID = ? AND IsActive = 1
                """, userId, warehouseId);
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private long userId(String email) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT UserID FROM [TRUpload].[dbo].[Users] WHERE Email = ?", Long.class, email);
        if (id == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable.");
        return id;
    }

    private void writeAudit(long actorId, String action, String entity, long entityId, String description) {
        jdbcTemplate.update("""
                INSERT INTO [TRUpload].[dbo].[AuditLogs] (ActorUserID, ActionType, EntityType, EntityID, Description)
                VALUES (?, ?, ?, ?, ?)
                """, actorId, action, entity, entityId, description);
    }

    private String normalizeDecision(String decision) {
        String value = decision == null ? "" : decision.trim().toUpperCase();
        if (!Set.of("APPROVED", "REJECTED").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED.");
        }
        return value;
    }

    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}