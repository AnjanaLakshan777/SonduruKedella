package com.sondurukedella.commissions.dto;

import com.sondurukedella.projects.entity.ProjectType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// One arrangement with the numbers needed to check it:
// amount = contractValue × ratePct / 100, rounded half-up to the cent.
// For a confirmed or voided arrangement amount is the stored value; for a draft it's a preview.
// paid counts active payments only; outstanding is what is still owed (0 for drafts and voids).
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
