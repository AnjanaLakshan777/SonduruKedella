package com.sondurukedella.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// The Dashboard KPIs, as of asOf (today in Colombo). Separate fields for separate meanings: agreed
// contract value, revenue recorded (billed), cash received and amounts still owed are never mixed.
public record DashboardReport(
        LocalDate asOf,
        Projects projects,
        Revenue revenue,
        Receivables receivables,
        ProjectResults projectResults,
        Payables payables,
        // FIFO value of the store now (open layers); a balance, not an expense
        BigDecimal inventoryValue,
        // financial year to date (Settings > Company), by month
        LocalDate financialYearStart,
        List<Month> monthly,
        List<String> notIncluded) {

    // active = IN_PROGRESS or ON_HOLD, as on the Dashboard
    public record Projects(long active, Map<String, Long> activeByType, Map<String, Long> byStatus, BigDecimal activeContractValue) {
    }

    // All time. revenueRecorded = billed installments net of applied delay deductions (date basis:
    // billed_on). paymentsReceived = active client receipts (payment_date); a write-off is not a payment.
    public record Revenue(BigDecimal revenueRecorded, BigDecimal paymentsReceived, BigDecimal writtenOff) {
    }

    // Now, from ReceivableService: billed installments only.
    public record Receivables(BigDecimal outstanding, BigDecimal overdue, long openInstallments, long overdueInstallments) {
    }

    // Sum of every project's ProjectFinancialReport: project costs and profit = revenue recorded −
    // written off − project costs. Estimated while projects are open; company expenses not included.
    public record ProjectResults(BigDecimal projectCosts, BigDecimal projectProfit, BigDecimal companyExpenses) {
    }

    // Still owed by the company now: manual expenses, supplier invoices and confirmed commission. Payroll is per week,
    // see GET /api/v1/reports/payroll.
    public record Payables(BigDecimal expenses, BigDecimal suppliers, BigDecimal commission) {
    }

    // revenueRecorded by billed_on, paymentsReceived by payment_date, manualExpenses by expense_date
    // (manual expenses only, project and company)
    public record Month(LocalDate month, BigDecimal revenueRecorded, BigDecimal paymentsReceived, BigDecimal manualExpenses) {
    }
}
