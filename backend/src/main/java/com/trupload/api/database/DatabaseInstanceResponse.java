package com.trupload.api.database;

import java.time.OffsetDateTime;

public record DatabaseInstanceResponse(
        String name,
        String status,
        OffsetDateTime createdAt) {
}
