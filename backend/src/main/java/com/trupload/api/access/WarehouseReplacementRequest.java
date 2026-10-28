package com.trupload.api.access;

import jakarta.validation.constraints.NotNull;

public record WarehouseReplacementRequest(
        @NotNull Long currentWarehouseId,
        @NotNull Long requestedWarehouseId) {
}