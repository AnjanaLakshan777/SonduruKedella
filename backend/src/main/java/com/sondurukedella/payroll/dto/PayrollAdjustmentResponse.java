package com.sondurukedella.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// advanceDeduction null = the default (everything outstanding, up to the gross)
public record PayrollAdjustmentResponse(
        long id,
        long employeeId,
        LocalDate weekStart,
        BigDecimal advanceDeduction,
        BigDecimal otherDeduction,
        String otherNote,
        long version) {
}
