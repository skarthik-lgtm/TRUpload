package com.trupload.api.database;

public record OrderFacilityResponse(
   String sourceId,
    String sourceFacilityId,
    String sourceFacilityCode,
    String sourceName,
    String destinationId,
    String division
) {}