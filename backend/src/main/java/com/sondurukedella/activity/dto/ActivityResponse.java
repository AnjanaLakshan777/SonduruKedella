package com.sondurukedella.activity.dto;

import com.sondurukedella.activity.entity.ActivityKind;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// projectId null = company-level. createdBy null when nobody was logged in (startup stuff).
public record ActivityResponse(long id, Long projectId, LocalDate date, ActivityKind kind, String message, OffsetDateTime createdAt,
        Long createdBy) {
}
