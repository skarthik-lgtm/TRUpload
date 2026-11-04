package com.trupload.api.access;

import jakarta.validation.constraints.NotBlank;

public record WarehouseAccessRequest(
        @NotBlank String requestType,
        Long currentWarehouseId,
        Long requestedWarehouseId) {
}