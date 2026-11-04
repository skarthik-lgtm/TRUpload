package com.trupload.model.auth.signup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SignupRequest(
                @NotBlank(message = "First name is required") String firstName,
                @NotBlank(message = "Last name is required") String lastName,
                @NotBlank(message = "Username is required") String username,
                @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must contain at least 8 characters") String password,
                @NotBlank(message = "Email is required") String email,
                List<Long> warehouseIds) {
}
