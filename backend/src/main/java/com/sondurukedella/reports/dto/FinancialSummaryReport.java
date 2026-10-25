package com.sondurukedella.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// Company-wide figures for a date range (inclusive). Each block names its date basis; the blocks are
// deliberately not added together, because they are recognised on different dates. position is a
// snapshot as of today, not a figure for the period.
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

    // billed_on: installments billed in the range, net of applied delay deductions.
    // received: payment_date, active receipts (they may settle billing from earlier periods).
    // writtenOff: write_off_date. otherIncome: income_date; otherIncomeReceived: its receipts by payment_date.
    public record Revenue(long installmentsBilled, BigDecimal billedAmount, BigDecimal deductions, BigDecimal revenueRecorded,
            long receipts, BigDecimal received, BigDecimal writtenOff, BigDecimal otherIncome, BigDecimal otherIncomeReceived) {
    }

    // expense_date, manual expenses only (ExpenseService.summary)
    public record Expenses(long count, BigDecimal projectExpenses, BigDecimal companyExpenses, BigDecimal total) {
    }

    // movement_date: stock issued to projects minus returns, at FIFO cost. Purchases into stock are
    // not here: they are stock value, not cost.
    public record Materials(BigDecimal issued, BigDecimal returned, BigDecimal net) {
    }

    // invoice_date. stockPurchases go into stock (stock value, not a cost); projectInvoices are goods
    // delivered to projects (project material cost). total = both, i.e. what suppliers billed.
    public record SupplierInvoices(long count, BigDecimal stockPurchases, BigDecimal projectInvoices, BigDecimal total) {
    }

    // Payroll weeks whose Monday falls in the range (PayrollService.weeks). labourCost = gross pay +
    // corrections; advances and payments are never cost. remainingPayable is still owed for weeks whose
    // payday has passed; remainingNotYetDue is for weeks not yet payable. firstWeek/lastWeek are null
    // when the range holds no Monday.
    public record Labour(LocalDate firstWeek, LocalDate lastWeek, int weeks, BigDecimal grossPay, BigDecimal corrections,
            BigDecimal labourCost, BigDecimal paid, BigDecimal remainingPayable, BigDecimal remainingNotYetDue) {
    }

    // confirmed: confirmed_on, arrangements still confirmed (voided ones excluded, as they are now).
    // paid: payment_date, active commission payments - they settle the cost, they are not another one.
    public record Commission(long confirmedCount, BigDecimal confirmed, BigDecimal paid) {
    }

    // As of asOf (today): balances, not period figures.
    public record Position(LocalDate asOf, BigDecimal receivablesOutstanding, BigDecimal receivablesOverdue, BigDecimal otherIncomeOutstanding,
            BigDecimal expensesOutstanding, BigDecimal suppliersOutstanding, BigDecimal commissionOutstanding, BigDecimal inventoryValue) {
    }
}
