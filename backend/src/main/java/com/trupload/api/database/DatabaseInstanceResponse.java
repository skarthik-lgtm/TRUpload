package com.trupload.api.database;

import java.util.List;
import java.time.Instant;

public record DatabaseInstanceResponse(
    String databaseName,
    List<RouteGroupResponse> routeGroups,
    Instant lastCheckDateTime
) {}