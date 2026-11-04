package com.trupload.api.access;

import java.time.LocalDateTime;
import java.util.List;

public record AccountRequestResponse(
        long accountRequestId,
        long userId,
        String firstName,
        String lastName,
        String username,
        String email,
        String accountStatus,
        String requestStatus,
        LocalDateTime requestedAt,
        List<WarehouseRequest> warehouses) {

    public record WarehouseRequest(
            long accountRequestWarehouseId,
            long warehouseId,
            String facility,
            String database,
            String uploadCode,
            String division,
            String requestStatus,
            String reviewComments) {
    }
}