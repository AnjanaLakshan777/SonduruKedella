package com.sondurukedella.audit.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// one record created, changed or deleted. changes: field -> [old, new]
public record AuditChangeResponse(
        long id,
        OffsetDateTime occurredAt,
        Long userId,
        String username,
        String httpMethod,
        String path,
        UUID requestId,
        String entityType,
        String entityId,
        String changeType,
        Map<String, List<String>> changes) {
}
