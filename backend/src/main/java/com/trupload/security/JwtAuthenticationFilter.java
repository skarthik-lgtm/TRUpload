package com.trupload.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JdbcTemplate sqlServerJdbcTemplate;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            @Qualifier("sqlServerJdbcTemplate") JdbcTemplate sqlServerJdbcTemplate) {
        this.jwtService = jwtService;
        this.sqlServerJdbcTemplate = sqlServerJdbcTemplate;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                String subject = jwtService.extractSubject(token);
                var currentRoles = sqlServerJdbcTemplate.query("""
                        SELECT TOP (1) R.RoleName
                        FROM [TRUpload].[dbo].[Users] U
                        JOIN [TRUpload].[dbo].[UserRoles] UR ON UR.UserID = U.UserID
                        JOIN [TRUpload].[dbo].[Roles] R ON R.RoleID = UR.RoleID
                        WHERE U.Email = ? AND U.Status = 'ACTIVE'
                        ORDER BY CASE WHEN R.RoleName = 'ADMIN' THEN 0 ELSE 1 END
                        """, (rs, rowNum) -> rs.getString("RoleName"), subject);
                if (!currentRoles.isEmpty()) {
                    String authority = "ADMIN".equalsIgnoreCase(currentRoles.get(0))
                            ? "ROLE_ADMIN"
                            : "ROLE_USER";
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            subject,
                            null,
                            java.util.List.of(new SimpleGrantedAuthority(authority)));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
