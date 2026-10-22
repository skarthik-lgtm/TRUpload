package com.trupload.api.database;

import java.math.BigDecimal;
import java.util.List;

public record RouteGroupResponse(
    String routeGroupId,
    int numberOfRoutes,
    long numberOfStops,
    long numberOfOrders,
    BigDecimal totalQ1,
    BigDecimal totalQ2,
    BigDecimal totalQ3,
    BigDecimal totalQ4,
    List<RouteDetailResponse> routes,
    List<OrderFacilityResponse> orderFacilities,
    List<String> validationErrors
) {}