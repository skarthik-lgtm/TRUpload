package com.trupload.api.access;

import java.util.List;

public record AdminUserResponse(
        long userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String status,
        String role,
        List<WarehouseResponse> warehouses) {
}