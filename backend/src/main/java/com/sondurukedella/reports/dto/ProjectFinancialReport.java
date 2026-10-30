package com.sondurukedella.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Management view, not a proper financial statement. Each number comes from its own module.
public record ProjectFinancialReport(
        LocalDate asOf,
        ProjectRefDto project,
        // not revenue, and deductions never change it
        BigDecimal contractValue,
        Revenue revenue,
        Costs costs,
        Commission commission,
        Profit profit,
        List<String> notIncluded) {

    // revenue counts when billed (minus deductions); receipts and write-offs only settle it
    public record Revenue(
            BigDecimal billedAmount,        // billed installments, before deductions
            BigDecimal deductions,          // delay deductions applied to billed installments
            BigDecimal revenueRecorded,     // billedAmount − deductions
            BigDecimal received,            // active (not voided) client receipts on billed installments
            BigDecimal writtenOff,
            BigDecimal outstanding,         // revenueRecorded − received − writtenOff, never below 0 per installment
            BigDecimal overdue,             // the part of outstanding past its due date
            BigDecimal unbilled) {          // scheduled installments not billed yet (not revenue)
    }

    // Each cost counted once, from its own module. Stock purchases, advances, payroll payments and
    // commission payments are never costs.
    public record Costs(
            BigDecimal manualExpenses,
            BigDecimal materials,
            BigDecimal supplierInvoices,
            BigDecimal labour,
            BigDecimal labourOpenWeeks,     // the part of labour from unpaid weeks, which can still change
            BigDecimal commission,
            BigDecimal total) {             // the sum of the five above, to the cent
    }

    public record Commission(BigDecimal committed, BigDecimal paid, BigDecimal outstanding, BigDecimal draftAmount) {
    }

    // profit = revenue - written off - costs. RECORDED once the project is completed, otherwise ESTIMATED.
    // margin is null until something is billed.
    public record Profit(BigDecimal profit, BigDecimal marginPct, ProfitBasis basis) {
    }

    public enum ProfitBasis { RECORDED, ESTIMATED }
}
