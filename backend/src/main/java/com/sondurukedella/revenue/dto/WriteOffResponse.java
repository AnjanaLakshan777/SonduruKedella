package com.sondurukedella.revenue.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record WriteOffResponse(
        long id,
        long installmentId,
        long projectId,
        LocalDate date,
        BigDecimal amount,
        String reason,
        OffsetDateTime createdAt) {
}
