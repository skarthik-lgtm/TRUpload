package com.trupload.api.upload;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FailedUploadRecord(
        long id,
        String databaseName,
        String routeGroupId,
        String routeId,
        LocalDate dispatchDate,
        String failureReason,
        LocalDateTime attemptedAt,
        String attemptedBy) {
}
