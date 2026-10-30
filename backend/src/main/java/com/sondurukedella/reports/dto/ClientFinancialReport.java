package com.sondurukedella.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// just the client's project reports added up
public record ClientFinancialReport(LocalDate asOf, long clientId, String clientName, Totals totals, List<ProjectFinancialReport> projects) {

    public record Totals(int projects, BigDecimal contractValue, BigDecimal revenueRecorded, BigDecimal received, BigDecimal writtenOff,
            BigDecimal outstanding, BigDecimal overdue, BigDecimal projectCosts, BigDecimal profit) {
    }
}
