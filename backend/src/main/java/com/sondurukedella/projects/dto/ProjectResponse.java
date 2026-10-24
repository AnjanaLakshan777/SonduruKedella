package com.sondurukedella.projects.dto;

import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// No received/costs/outstanding figures on purpose: they need Revenue and Expenses, and
// returning zeros would look like real numbers.
public record ProjectResponse(
        long id,
        String code,
        String name,
        ProjectType type,
        long clientId,
        String clientName,
        String location,
        String description,
        BigDecimal contractValue,
        LocalDate startDate,
        LocalDate expectedCompletion,
        LocalDate actualCompletion,
        ProjectStatus status,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
}
