package com.sondurukedella.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// voided corrections are kept but no longer change what is owed or the labour cost
public record PayrollCorrectionResponse(
        long id,
        long employeeId,
        LocalDate weekStart,
        LocalDate correctionDate,
        BigDecimal wageDelta,
        Long projectId,
        String reason,
        OffsetDateTime createdAt,
        UUID requestId,
        boolean voided,
        OffsetDateTime voidedAt,
        String voidReason,
        long version) {
}
