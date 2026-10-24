package com.sondurukedella.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PayrollCorrectionResponse(
        long id,
        long employeeId,
        LocalDate weekStart,
        LocalDate correctionDate,
        BigDecimal wageDelta,
        Long projectId,
        String reason,
        OffsetDateTime createdAt) {
}
