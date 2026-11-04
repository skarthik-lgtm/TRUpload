package com.trupload.api.upload;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SuccessfulUploadRecord(
        long id,
        String databaseName,
        String routeGroupId,
        String routeId,
        LocalDate dispatchDate,
        LocalDateTime uploadedAt,
        String uploadedBy,
        String warnings) {
}
