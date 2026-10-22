package com.trupload.api.database;

import java.util.List;

public record RouteResponse(
        String routeId,
        String groupId,
        List<OrderResponse> orders,
        List<RouteActivityResponse> routeActivities
) {
}