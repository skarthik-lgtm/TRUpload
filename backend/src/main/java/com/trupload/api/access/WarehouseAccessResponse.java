package com.trupload.api.access;

import java.time.LocalDateTime;

public record WarehouseAccessResponse(
        long warehouseAccessRequestId,
        long userId,
        String username,
        String email,
        String requestType,
        Long currentWarehouseId,
        String currentFacility,
        Long requestedWarehouseId,
        String requestedFacility,
        String requestStatus,
        LocalDateTime requestedAt,
        String reviewComments) {
}