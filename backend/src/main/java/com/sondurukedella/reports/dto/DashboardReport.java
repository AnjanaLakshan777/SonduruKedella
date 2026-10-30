package com.sondurukedella.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Contract value, billed revenue, cash in and what's still owed are kept as separate fields on purpose.
public record DashboardReport(
        LocalDate asOf,
        Projects projects,
        Revenue revenue,
        Receivables receivables,
        ProjectResults projectResults,
        Payables payables,
        // current FIFO stock value - a balance, not an expense
        BigDecimal inventoryValue,
        // from the start of the financial year (Settings > Company)
        LocalDate financialYearStart,
        List<Month> monthly,
        List<String> notIncluded) {

    // active = IN_PROGRESS + ON_HOLD, same as the UI
    public record Projects(long active, Map<String, Long> activeByType, Map<String, Long> byStatus, BigDecimal activeContractValue) {
    }

    // all time. revenueRecorded = billed minus delay deductions; a write-off isn't a payment.
    // other income is shown separately from project revenue.
    public record Revenue(BigDecimal revenueRecorded, BigDecimal paymentsReceived, BigDecimal writtenOff, BigDecimal otherIncome,
            BigDecimal otherIncomeReceived) {
    }

    // billed installments only
    public record Receivables(BigDecimal outstanding, BigDecimal overdue, long openInstallments, long overdueInstallments,
            BigDecimal otherIncomeOutstanding, BigDecimal otherIncomeOverdue) {
    }

    // every project's own report added up; company expenses aren't project costs
    public record ProjectResults(BigDecimal projectCosts, BigDecimal projectProfit, BigDecimal companyExpenses) {
    }

    // payroll isn't here because it's per week - see /reports/payroll
    public record Payables(BigDecimal expenses, BigDecimal suppliers, BigDecimal commission) {
    }

    // manual expenses only
    public record Month(LocalDate month, BigDecimal revenueRecorded, BigDecimal paymentsReceived, BigDecimal manualExpenses) {
    }
}
