package com.sondurukedella.commissions.dto;

import com.sondurukedella.projects.entity.ProjectType;
import java.time.LocalDate;

// recipient = exact name (case/spaces ignored), q = partial match. from/to are on confirmedOn.
public record CommissionFilter(
        String recipient,
        String q,
        Long projectId,
        ProjectType type,
        CommissionState status,
        LocalDate from,
        LocalDate to) {
}
