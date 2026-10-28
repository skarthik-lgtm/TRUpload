package com.trupload.api.access;

import jakarta.validation.constraints.NotBlank;

public record UserRoleRequest(@NotBlank String role) {
}