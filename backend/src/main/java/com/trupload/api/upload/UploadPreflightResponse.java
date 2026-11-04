package com.trupload.api.upload;

import java.util.List;
import java.time.LocalDate;

public record UploadPreflightResponse(
        String databaseName,
        String routeGroupId,
        int routeCount,
        boolean valid,
        boolean shuttle,
        List<String> groupReasons,
        List<UploadRecordResult> routes,
        List<RouteValidation> routeValidations) {

    public record RouteValidation(
            String routeId,
            LocalDate dispatchDate,
            List<Check> checks) {
    }

    public record Check(
            String name,
            boolean passed,
            String message) {
    }
}