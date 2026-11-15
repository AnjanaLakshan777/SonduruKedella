package com.sondurukedella.audit.dto;

import java.time.OffsetDateTime;

// a row of security_events: sign-ins and account changes
public record SecurityEventResponse(
        long id,
        OffsetDateTime occurredAt,
        String eventType,
        Long actorUserId,
        Long targetUserId,
        String username,
        String remoteAddress,
        String details) {
}
