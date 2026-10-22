package com.trupload.api.upload;

import java.time.LocalDate;
import java.util.List;

public record UploadRecordResult(
        String databaseName,
        String routeGroupId,
        String routeId,
        LocalDate dispatchDate,
        String status,
        List<String> reasons,
        boolean shuttle) {
}
