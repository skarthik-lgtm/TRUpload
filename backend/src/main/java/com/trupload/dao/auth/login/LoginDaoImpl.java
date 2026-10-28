package com.trupload.dao.auth.login;

import java.util.Locale;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.trupload.model.auth.signup.SignupUser;

@Repository
public class LoginDaoImpl implements LoginDao {

    private final JdbcTemplate jdbcTemplate;

    public LoginDaoImpl(@Qualifier("sqlServerJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SignupUser> findByLoginIdentifier(String identifier) {
        return jdbcTemplate.query("""
                SELECT TOP (1) UserID, Username, Email, PasswordHash, FirstName, LastName,
                       Status, CreatedAt, LastLoginAt
                FROM [TRUpload].[dbo].[Users]
                WHERE LOWER(LTRIM(RTRIM(Email))) = ?
                   OR LOWER(LTRIM(RTRIM(Username))) = ?
                """, (rs, rowNum) -> new SignupUser(
                rs.getLong("UserID"), rs.getString("Username"), rs.getString("Email"),
                rs.getString("FirstName"), rs.getString("LastName"), rs.getString("PasswordHash"),
                rs.getString("Status"), rs.getTimestamp("CreatedAt").toLocalDateTime(),
                rs.getTimestamp("LastLoginAt") == null ? null : rs.getTimestamp("LastLoginAt").toLocalDateTime()),
                identifier.toLowerCase(Locale.ROOT), identifier.toLowerCase(Locale.ROOT))
                .stream().findFirst();
    }

    @Override
    public Optional<Long> findRoleIdByUserId(Long userId) {
        List<Long> results = jdbcTemplate.query("""
                SELECT TOP (1) RoleID FROM [TRUpload].[dbo].[UserRoles]
                WHERE UserID = ? ORDER BY RoleID
                """, (rs, rowNum) -> rs.getLong("RoleID"), userId);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    @Override
    public Optional<String> findRoleNameByUserId(Long userId) {
        List<String> roles = jdbcTemplate.query("""
                SELECT TOP (1) R.RoleName
                FROM [TRUpload].[dbo].[UserRoles] UR
                JOIN [TRUpload].[dbo].[Roles] R ON R.RoleID = UR.RoleID
                WHERE UR.UserID = ? ORDER BY UR.RoleID
                """, (rs, rowNum) -> rs.getString("RoleName"), userId);
        return roles.stream().findFirst();
    }

    @Override
    public void markLoginNow(Long userId) {
        jdbcTemplate.update("""
                UPDATE [TRUpload].[dbo].[Users]
                SET LastLoginAt = SYSUTCDATETIME(), UpdatedAt = SYSUTCDATETIME()
                WHERE UserID = ? AND Status = 'ACTIVE'
                """, userId);
    }
}