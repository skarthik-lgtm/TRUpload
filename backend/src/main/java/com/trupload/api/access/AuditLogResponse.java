package com.trupload.api.access;

import java.time.LocalDateTime;

public record AuditLogResponse(
        long auditLogId,
        Long actorUserId,
        String actorEmail,
        String actionType,
        String entityType,
        Long entityId,
        String description,
        LocalDateTime createdAt) {
}