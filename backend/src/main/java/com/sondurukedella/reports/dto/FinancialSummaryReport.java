package com.sondurukedella.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// Each block uses its own date (billing date, payment date, etc), so we don't add them up into one
// "profit" figure. position is a snapshot as of today.
public record FinancialSummaryReport(
        LocalDate from,
        LocalDate to,
        Revenue revenue,
        Expenses expenses,
        Materials materials,
        SupplierInvoices supplierInvoices,
        Labour labour,
        Commission commission,
        Position position) {

    // revenue by billed_on, received by payment_date (can be for older bills), write-offs by write_off_date
    public record Revenue(long installmentsBilled, BigDecimal billedAmount, BigDecimal deductions, BigDecimal revenueRecorded,
            long receipts, BigDecimal received, BigDecimal writtenOff, BigDecimal otherIncome, BigDecimal otherIncomeReceived) {
    }

    // manual expenses, by expense_date
    public record Expenses(long count, BigDecimal projectExpenses, BigDecimal companyExpenses, BigDecimal total) {
    }

    // issues minus returns at FIFO cost. Buying stock isn't a cost so it's not here.
    public record Materials(BigDecimal issued, BigDecimal returned, BigDecimal net) {
    }

    // stock purchases aren't a cost (stock value); project invoices are
    public record SupplierInvoices(long count, BigDecimal stockPurchases, BigDecimal projectInvoices, BigDecimal total) {
    }

    // weeks whose Monday is in the range. labourCost = gross + corrections (advances and payments aren't
    // costs). first/lastWeek are null if the range has no Monday.
    public record Labour(LocalDate firstWeek, LocalDate lastWeek, int weeks, BigDecimal grossPay, BigDecimal corrections,
            BigDecimal labourCost, BigDecimal paid, BigDecimal remainingPayable, BigDecimal remainingNotYetDue) {
    }

    // voided arrangements don't count; payments aren't an extra cost
    public record Commission(long confirmedCount, BigDecimal confirmed, BigDecimal paid) {
    }

    public record Position(LocalDate asOf, BigDecimal receivablesOutstanding, BigDecimal receivablesOverdue, BigDecimal otherIncomeOutstanding,
            BigDecimal expensesOutstanding, BigDecimal suppliersOutstanding, BigDecimal commissionOutstanding, BigDecimal inventoryValue) {
    }
}
