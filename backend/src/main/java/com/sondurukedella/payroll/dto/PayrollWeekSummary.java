package com.sondurukedella.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// One row of the period summary: a week's totals without its lines.
public record PayrollWeekSummary(
        LocalDate weekStart,
        LocalDate weekEnd,
        boolean payable,
        int workers,
        BigDecimal grossPay,
        BigDecimal netPay,
        BigDecimal corrections,
        BigDecimal paid,
        BigDecimal remaining) {
}
