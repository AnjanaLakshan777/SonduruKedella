package com.sondurukedella.revenue.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// voided = reversed. still listed, but doesn't reduce what's owed anymore
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
