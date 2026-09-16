package com.trupload.model.auth.signup;

import java.time.LocalDateTime;

public record SignupResponse(
        String message,
        String username,
        String email,
        String role,
        LocalDateTime createdDate) {
}
