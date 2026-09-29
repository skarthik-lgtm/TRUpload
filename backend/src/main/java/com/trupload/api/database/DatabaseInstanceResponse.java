package com.trupload.api.database;
import java.util.List;
import java.time.OffsetDateTime;

public record DatabaseInstanceResponse(
        String database,
        List<String> groupIds) {
}
