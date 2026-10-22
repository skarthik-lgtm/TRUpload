package com.trupload.api.upload;

import java.util.List;
import java.util.UUID;

public record UploadResponse(
        UUID uploadBatchId,
        int uploadedCount,
        int failedCount,
        List<String> shuttleWarnings,
        List<UploadRecordResult> results) {
}
