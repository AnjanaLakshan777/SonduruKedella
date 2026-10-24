package com.sondurukedella.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// One worker's week. frozen = paid (or closed): the figures are the payroll_lines snapshot. Otherwise
// they are worked out live from attendance, overtime, advances and the adjustment, and can still change.
//   owed      = netPay + corrections
//   remaining = what is still to pay (never below 0); overpaid > 0 when a negative correction left the
//               worker paid more than is now owed
// absentDays and pendingOvertimeHours are only known live (null once frozen). blockedReason says why the
// week can't be paid yet, when it can't.
public record PayrollLineResponse(
        long employeeId,
        String employee,
        String role,
        LocalDate weekStart,
        LocalDate weekEnd,
        PayrollLineStatus status,
        boolean frozen,
        int presentDays,
        int halfDays,
        Integer absentDays,
        BigDecimal payableDays,
        BigDecimal dailyWage,
        BigDecimal basePay,
        BigDecimal overtimeHours,
        BigDecimal pendingOvertimeHours,
        BigDecimal overtimePay,
        BigDecimal grossPay,
        BigDecimal advancesOutstanding,
        BigDecimal advanceDeduction,
        BigDecimal otherDeduction,
        String otherNote,
        BigDecimal netPay,
        BigDecimal corrections,
        BigDecimal owed,
        BigDecimal paid,
        BigDecimal remaining,
        BigDecimal overpaid,
        List<LabourCost> costs,
        String blockedReason) {

    // projectId null = labour not tied to a project (a company cost)
    public record LabourCost(Long projectId, BigDecimal amount) {
    }
}
