package com.sondurukedella.reports.dto;

import com.sondurukedella.payroll.dto.PayrollWeekSummary;
import java.util.List;

public record PayrollReport(FinancialSummaryReport.Labour totals, List<PayrollWeekSummary> weeks) {
}
