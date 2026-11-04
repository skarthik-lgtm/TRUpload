package com.trupload.api.access;

import jakarta.validation.constraints.NotBlank;

public record UserStatusRequest(@NotBlank String status) {
}