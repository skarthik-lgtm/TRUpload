package com.trupload.api.database;

import java.time.LocalDateTime;

public record RouteDetailResponse(
    String routeId,
    String routeGroupId,
    String comment,
    LocalDateTime dispatchDateTime,
    String expectedDayName,
    String actualDayName,
    String result
) {}