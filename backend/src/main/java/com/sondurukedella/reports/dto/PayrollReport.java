package com.sondurukedella.reports.dto;

import com.sondurukedella.payroll.dto.PayrollWeekSummary;
import java.util.List;

// Payroll weeks whose Monday falls in the range, newest first (PayrollService.weeks), with their totals.
// No employee details beyond the worker count.
public record PayrollReport(FinancialSummaryReport.Labour totals, List<PayrollWeekSummary> weeks) {
}
