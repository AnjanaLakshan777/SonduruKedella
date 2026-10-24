package com.sondurukedella.revenue.service;

import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// The agreed delay-deduction formula, same as calcDelay() in the frontend's lib/finance.ts:
//   daysLate   = max(0, completion - contract deadline)
//   chargeable = max(0, daysLate - grace days)
//   PER_DAY          chargeable x rate per day
//   PERCENT_PER_WEEK full weeks (chargeable / 7, rounded down) x percent x contract value / 100
//   FIXED_AMOUNT     the fixed amount, once chargeable > 0
// then capped at max_deduction when there is one. Money is rounded HALF_UP to 2 decimals.
public final class DelayDeductionCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private DelayDeductionCalculator() {
    }

    public static Calculation calculate(DelayDeductionTerms terms, BigDecimal contractValue, LocalDate completion) {
        long daysLate = Math.max(0, ChronoUnit.DAYS.between(terms.getContractDeadline(), completion));
        long chargeable = Math.max(0, daysLate - terms.getGraceDays());

        BigDecimal calculated = switch (terms.getRule()) {
            case PER_DAY -> terms.getRatePerDay().multiply(BigDecimal.valueOf(chargeable));
            case PERCENT_PER_WEEK -> contractValue.multiply(terms.getPercentPerWeek())
                    .multiply(BigDecimal.valueOf(chargeable / 7))
                    .divide(HUNDRED, Money.SCALE, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> chargeable > 0 ? terms.getFixedAmount() : BigDecimal.ZERO;
        };
        calculated = Money.amount(calculated);

        BigDecimal cap = terms.getMaxDeduction();
        boolean capped = cap != null && calculated.compareTo(cap) > 0;
        BigDecimal finalAmount = capped ? Money.amount(cap) : calculated;
        return new Calculation(daysLate, chargeable, calculated, cap == null ? null : Money.amount(cap), capped, finalAmount);
    }

    public record Calculation(
            long daysLate,
            long chargeableDays,
            BigDecimal calculatedAmount,
            BigDecimal maxDeduction,
            boolean capped,
            BigDecimal finalAmount) {
    }
}
