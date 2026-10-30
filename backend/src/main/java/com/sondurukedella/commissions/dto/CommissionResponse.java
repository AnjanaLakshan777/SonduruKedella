package com.sondurukedella.commissions.dto;

import com.sondurukedella.projects.entity.ProjectType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// amount = contractValue * ratePct / 100. Stored once confirmed, just a preview for drafts.
// outstanding is 0 for drafts and voided ones.
public record CommissionResponse(
        long id,
        long projectId,
        String projectCode,
        String projectName,
        ProjectType projectType,
        BigDecimal contractValue,
        String recipient,
        String role,
        BigDecimal ratePct,
        BigDecimal amount,
        BigDecimal paid,
        BigDecimal outstanding,
        CommissionState status,
        LocalDate confirmedOn,
        boolean policyException,
        OffsetDateTime voidedAt,
        String voidReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
}
