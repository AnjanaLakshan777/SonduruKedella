package com.sondurukedella.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// A client's projects, each as its own ProjectFinancialReport, plus their sums. Same definitions as the
// project report: revenue recorded = billed net of deductions; profit = revenue − written off − costs.
public record ClientFinancialReport(LocalDate asOf, long clientId, String clientName, Totals totals, List<ProjectFinancialReport> projects) {

    public record Totals(int projects, BigDecimal contractValue, BigDecimal revenueRecorded, BigDecimal received, BigDecimal writtenOff,
            BigDecimal outstanding, BigDecimal overdue, BigDecimal projectCosts, BigDecimal profit) {
    }
}
