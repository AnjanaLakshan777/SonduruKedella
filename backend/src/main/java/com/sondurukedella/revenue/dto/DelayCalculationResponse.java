package com.sondurukedella.revenue.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// What the agreed terms work out to for a completion date. Read-only - nothing is applied.
public record DelayCalculationResponse(
        LocalDate completionDate,
        LocalDate contractDeadline,
        long daysLate,
        int graceDays,
        long chargeableDays,
        BigDecimal calculatedAmount,
        BigDecimal maxDeduction,
        boolean capped,
        BigDecimal finalAmount) {
}
