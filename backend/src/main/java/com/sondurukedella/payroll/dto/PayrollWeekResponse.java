package com.sondurukedella.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// A payroll week (Monday to Sunday, paid on weekEnd = the payday in Settings > Wages). payable is
// whether payday has come. The totals are sums of the lines.
public record PayrollWeekResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        boolean payable,
        int workers,
        BigDecimal grossPay,
        BigDecimal advanceDeductions,
        BigDecimal otherDeductions,
        BigDecimal netPay,
        BigDecimal corrections,
        BigDecimal paid,
        BigDecimal remaining,
        List<PayrollLineResponse> lines) {
}
