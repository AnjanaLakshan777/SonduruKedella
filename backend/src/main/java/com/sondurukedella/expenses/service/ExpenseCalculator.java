package com.sondurukedella.expenses.service;

import com.sondurukedella.common.money.Money;
import com.sondurukedella.expenses.dto.ExpensePaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

// The expense balance and status, in one place so the API, the totals, the status filter and the
// overpayment check all agree. Same rules as lib/finance.ts:
//   outstanding = max(0, amount - active payments)
//   PAID when nothing is outstanding, else OVERDUE once the due date has passed,
//   else PARTIALLY_PAID if anything was paid, else UNPAID.
public final class ExpenseCalculator {

    private ExpenseCalculator() {
    }

    public static Balance of(BigDecimal amount, BigDecimal paid, LocalDate dueDate, LocalDate today) {
        BigDecimal outstanding = Money.amount(amount.subtract(paid)).max(BigDecimal.ZERO.setScale(Money.SCALE));
        ExpensePaymentStatus status;
        if (outstanding.signum() == 0) {
            status = ExpensePaymentStatus.PAID;
        } else if (dueDate != null && dueDate.isBefore(today)) {
            status = ExpensePaymentStatus.OVERDUE;
        } else if (paid.signum() > 0) {
            status = ExpensePaymentStatus.PARTIALLY_PAID;
        } else {
            status = ExpensePaymentStatus.UNPAID;
        }
        return new Balance(Money.amount(paid), outstanding, status);
    }

    public record Balance(BigDecimal paid, BigDecimal outstanding, ExpensePaymentStatus status) {
    }
}
