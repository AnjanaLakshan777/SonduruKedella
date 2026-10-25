package com.sondurukedella.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// One project's management view (not a formal financial statement), as of asOf (today in Colombo).
// Every figure comes from the owning module's service; see docs/ARCHITECTURE.md "Reports".
public record ProjectFinancialReport(
        LocalDate asOf,
        ProjectRefDto project,
        // agreed with the client; never revenue by itself and never changed by deductions
        BigDecimal contractValue,
        Revenue revenue,
        Costs costs,
        Commission commission,
        Profit profit,
        // sources that would belong here but don't exist in the system yet
        List<String> notIncluded) {

    // Revenue module (ReceivableService). Revenue is recognised at billing, net of the applied delay
    // deduction; receipts settle it; write-offs reduce what can be collected and are not receipts.
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

    // Project costs, each from its own module, counted once:
    //   manualExpenses  Expenses module, PROJECT scope (no labour or commission category can be used)
    //   materials       Inventory: issued − returned to this project at the FIFO cost fixed on each movement
    //   labour          Payroll: frozen weeks' split + corrections + open weeks priced live (labourOpenWeeks)
    //   commission      Commissions: confirmed arrangements' stored amounts (drafts and voids excluded)
    // Purchases into stock, payroll advances and payments, and commission payments are never costs.
    public record Costs(
            BigDecimal manualExpenses,
            BigDecimal materials,
            BigDecimal labour,
            BigDecimal labourOpenWeeks,     // the part of labour from unpaid weeks, which can still change
            BigDecimal commission,
            BigDecimal total) {             // the sum of the four above, to the cent
    }

    public record Commission(BigDecimal committed, BigDecimal paid, BigDecimal outstanding, BigDecimal draftAmount) {
    }

    // profit = revenueRecorded − writtenOff − costs.total (DATABASE.md "Profitability").
    // basis RECORDED once the project is COMPLETED, otherwise ESTIMATED. marginPct = profit ÷
    // revenueRecorded × 100, rounded half-up to 2 places; null when nothing has been billed yet.
    public record Profit(BigDecimal profit, BigDecimal marginPct, ProfitBasis basis) {
    }

    public enum ProfitBasis { RECORDED, ESTIMATED }
}
