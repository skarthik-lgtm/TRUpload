package com.trupload.api.upload;

import java.util.List;
import java.util.UUID;

public record UploadResponse(
        UUID uploadBatchId,
        int generatedCount,
        int failedCount,
        List<String> shuttleWarnings,
        List<String> generatedFiles,
        List<UploadRecordResult> results) {
}
