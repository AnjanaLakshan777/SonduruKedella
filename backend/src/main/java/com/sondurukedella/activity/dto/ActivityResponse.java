package com.sondurukedella.activity.dto;

import com.sondurukedella.activity.entity.ActivityKind;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// One feed entry. projectId is null for company-level entries; createdBy is the app_users id of whoever
// did it (null when nobody was signed in, e.g. startup work).
public record ActivityResponse(long id, Long projectId, LocalDate date, ActivityKind kind, String message, OffsetDateTime createdAt,
        Long createdBy) {
}
