package com.trupload.api.access;

public record WarehouseResponse(
        long warehouseId,
        String facility,
        String database,
        String uploadCode,
        String division) {
}