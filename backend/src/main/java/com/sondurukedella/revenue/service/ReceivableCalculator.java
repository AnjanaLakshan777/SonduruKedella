package com.sondurukedella.revenue.service;

import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.dto.ReceivableStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// The one place the receivable formula lives, so the API, the totals and every check before a
// receipt or write-off agree. Pure arithmetic: callers pass in the stored figures and "today".
public final class ReceivableCalculator {

    private ReceivableCalculator() {
    }

    // net         = amount - deduction applied to this installment
    // outstanding = net - receipts - write-offs, never below 0; and 0 for unbilled installments,
    //               because only billed installments are receivables at all
    public static Receivable of(BigDecimal amount, boolean billed, LocalDate dueDate, BigDecimal deduction,
            BigDecimal received, BigDecimal writtenOff, LocalDate today) {
        BigDecimal net = Money.amount(amount.subtract(deduction)).max(BigDecimal.ZERO.setScale(Money.SCALE));
        BigDecimal settled = received.add(writtenOff);
        BigDecimal outstanding = billed
                ? Money.amount(net.subtract(settled)).max(BigDecimal.ZERO.setScale(Money.SCALE))
                : BigDecimal.ZERO.setScale(Money.SCALE);

        ReceivableStatus status;
        if (!billed) {
            status = ReceivableStatus.UNBILLED;
        } else if (outstanding.signum() == 0) {
            status = ReceivableStatus.PAID;
        } else if (dueDate.isBefore(today)) {
            // overdue wins over partially paid, as in the UI: a late part-payment is still late
            status = ReceivableStatus.OVERDUE;
        } else if (settled.signum() > 0) {
            status = ReceivableStatus.PARTIALLY_PAID;
        } else {
            status = ReceivableStatus.BILLED;
        }
        long daysOverdue = status == ReceivableStatus.OVERDUE ? ChronoUnit.DAYS.between(dueDate, today) : 0;
        return new Receivable(Money.amount(deduction), net, Money.amount(received), Money.amount(writtenOff),
                outstanding, status, daysOverdue);
    }

    public record Receivable(
            BigDecimal deduction,
            BigDecimal net,
            BigDecimal received,
            BigDecimal writtenOff,
            BigDecimal outstanding,
            ReceivableStatus status,
            long daysOverdue) {

        // receipts or write-offs have touched it - what the Receivables KPI calls "partially paid"
        public boolean partlySettled() {
            return outstanding.signum() > 0 && received.add(writtenOff).signum() > 0;
        }
    }
}
