package com.trupload.api.database;

public record RouteActivityResponse(
        String routeId,
        String routeGroupId,
        Object activityClassType,
        Object sequenceNumber,
        Object routeStopIndex,
        String locationId,
        Object duration,
        Object distance
) {
}