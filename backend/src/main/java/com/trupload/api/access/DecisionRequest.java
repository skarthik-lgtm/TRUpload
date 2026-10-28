package com.trupload.api.access;

import jakarta.validation.constraints.NotBlank;

public record DecisionRequest(
        @NotBlank String decision,
        String comments) {
}