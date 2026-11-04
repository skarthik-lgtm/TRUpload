package com.trupload.api.access;

import jakarta.validation.constraints.NotNull;

public record UserWarehouseRequest(@NotNull Long warehouseId) {
}