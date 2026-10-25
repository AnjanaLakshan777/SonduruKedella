package com.sondurukedella.revenue.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// reversed write-offs (voided = true) are kept and listed but no longer reduce what is owed
public record WriteOffResponse(
        long id,
        long installmentId,
        long projectId,
        LocalDate date,
        BigDecimal amount,
        String reason,
        OffsetDateTime createdAt,
        boolean voided,
        OffsetDateTime voidedAt,
        String voidReason,
        long version) {
}
