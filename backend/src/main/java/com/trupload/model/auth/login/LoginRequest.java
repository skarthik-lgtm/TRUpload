package com.trupload.model.auth.login;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username or email is required")
        String email,
        @NotBlank(message = "Password is required")
        String password,
        String username,
        LocalDateTime createdDate,
        String role,
        LocalDateTime lastLoginDate) {
}
