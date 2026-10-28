package com.trupload.dao.auth.signup;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.trupload.model.auth.signup.SignupUser;

@Repository
public class SignupDaoImpl implements SignupDao {

    private final JdbcTemplate jdbcTemplate;

    public SignupDaoImpl(@Qualifier("sqlServerJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByEmail(String email) {
        return exists("Email", email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return exists("Username", username);
    }

    @Override
    public SignupUser save(SignupUser user) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO [TRUpload].[dbo].[Users]
                    (Username, Email, PasswordHash, FirstName, LastName, Status)
                OUTPUT INSERTED.UserID
                VALUES (?, ?, ?, ?, ?, 'PENDING')
                """, Long.class,
                user.getUsername(), user.getEmail(), user.getPassword(),
                user.getFirstName(), user.getLastName());
        user.setId(id);
        return user;
    }

    @Override
    public void assignRole(Long userId, String roleName) {
        jdbcTemplate.update("""
                INSERT INTO [TRUpload].[dbo].[UserRoles] (UserID, RoleID)
                SELECT ?, RoleID FROM [TRUpload].[dbo].[Roles] WHERE RoleName = ?
                """, userId, roleName);
    }

    @Override
    public long createAccountRequest(Long userId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO [TRUpload].[dbo].[AccountRequests] (UserID, RequestStatus)
                OUTPUT INSERTED.AccountRequestID
                VALUES (?, 'PENDING')
                """, Long.class, userId);
    }

    @Override
    public void addRequestedWarehouse(long accountRequestId, long warehouseId) {
        jdbcTemplate.update("""
                INSERT INTO [TRUpload].[dbo].[AccountRequestWarehouses]
                    (AccountRequestID, WarehouseID, RequestStatus)
                VALUES (?, ?, 'PENDING')
                """, accountRequestId, warehouseId);
    }

    @Override
    public boolean warehouseExists(long warehouseId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM [TRUpload].[dbo].[Warehouses] WHERE WarehouseID = ?
                """, Integer.class, warehouseId);
        return count != null && count > 0;
    }

    private boolean exists(String column, String value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM [TRUpload].[dbo].[Users] WHERE " + column + " = ?",
                Integer.class, value);
        return count != null && count > 0;
    }
}
