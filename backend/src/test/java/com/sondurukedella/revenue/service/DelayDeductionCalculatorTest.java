package com.sondurukedella.revenue.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sondurukedella.revenue.entity.DelayDeductionTerms;
import com.sondurukedella.revenue.entity.DelayRule;
import com.sondurukedella.revenue.service.DelayDeductionCalculator.Calculation;
import com.sondurukedella.support.TestEntities;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DelayDeductionCalculatorTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, 9, 30);
    private static final BigDecimal CONTRACT = new BigDecimal("10000000.00");

    @Test
    void theWorkedExampleFromTheDocs() {
        // 13 days late, 3 grace days: 10 chargeable days x 15,000 = 150,000
        Calculation c = calc(terms(DelayRule.PER_DAY, "15000", null, null, 3, null), DEADLINE.plusDays(13));

        assertThat(c.daysLate()).isEqualTo(13);
        assertThat(c.chargeableDays()).isEqualTo(10);
        assertThat(c.calculatedAmount()).isEqualByComparingTo("150000.00");
        assertThat(c.finalAmount()).isEqualByComparingTo("150000.00");
        assertThat(c.capped()).isFalse();
    }

    @Test
    void finishingOnOrBeforeTheDeadlineOrInsideTheGracePeriodCostsNothing() {
        DelayDeductionTerms perDay = terms(DelayRule.PER_DAY, "15000", null, null, 3, null);
        assertThat(calc(perDay, DEADLINE.minusDays(5)).daysLate()).isZero();
        assertThat(calc(perDay, DEADLINE).finalAmount()).isEqualByComparingTo("0");
        assertThat(calc(perDay, DEADLINE.plusDays(3)).chargeableDays()).isZero(); // exactly the grace period
        assertThat(calc(perDay, DEADLINE.plusDays(4)).finalAmount()).isEqualByComparingTo("15000");
    }

    @Test
    void percentPerWeekCountsOnlyFullWeeksOfTheContractValue() {
        DelayDeductionTerms pct = terms(DelayRule.PERCENT_PER_WEEK, null, "0.5", null, 0, null);

        assertThat(calc(pct, DEADLINE.plusDays(6)).finalAmount()).isEqualByComparingTo("0"); // under a week
        assertThat(calc(pct, DEADLINE.plusDays(7)).finalAmount()).isEqualByComparingTo("50000.00");
        assertThat(calc(pct, DEADLINE.plusDays(20)).finalAmount()).isEqualByComparingTo("100000.00"); // 2 full weeks
    }

    @Test
    void percentResultsAreRoundedHalfUpToCents() {
        // 1,000,001 x 0.3333% x 1 week = 3333.003333 -> 3333.00; x 0.0005% -> 5.000005 -> 5.00
        DelayDeductionTerms pct = terms(DelayRule.PERCENT_PER_WEEK, null, "0.3333", null, 0, null);
        assertThat(DelayDeductionCalculator.calculate(pct, new BigDecimal("1000001.00"), DEADLINE.plusDays(7))
                .finalAmount()).isEqualByComparingTo("3333.00");
        DelayDeductionTerms half = terms(DelayRule.PERCENT_PER_WEEK, null, "0.0015", null, 0, null);
        // 1,000,000 x 0.0015% = 15.00 exactly; 1,000,333 x 0.0015% = 15.004995 -> 15.00; 1,000,334 -> 15.00501 -> 15.01
        assertThat(DelayDeductionCalculator.calculate(half, new BigDecimal("1000334.00"), DEADLINE.plusDays(7))
                .finalAmount()).isEqualByComparingTo("15.01");
    }

    @Test
    void aFixedAmountAppliesOnceTheWorkIsChargeablyLate() {
        DelayDeductionTerms fixed = terms(DelayRule.FIXED_AMOUNT, null, null, "250000", 2, null);
        assertThat(calc(fixed, DEADLINE.plusDays(2)).finalAmount()).isEqualByComparingTo("0");
        assertThat(calc(fixed, DEADLINE.plusDays(3)).finalAmount()).isEqualByComparingTo("250000.00");
        assertThat(calc(fixed, DEADLINE.plusDays(300)).finalAmount()).isEqualByComparingTo("250000.00");
    }

    @Test
    void theMaximumDeductionCapsTheResult() {
        DelayDeductionTerms capped = terms(DelayRule.PER_DAY, "15000", null, null, 0, "100000");

        Calculation over = calc(capped, DEADLINE.plusDays(10));
        assertThat(over.calculatedAmount()).isEqualByComparingTo("150000.00");
        assertThat(over.capped()).isTrue();
        assertThat(over.finalAmount()).isEqualByComparingTo("100000.00");

        Calculation under = calc(capped, DEADLINE.plusDays(2));
        assertThat(under.capped()).isFalse();
        assertThat(under.finalAmount()).isEqualByComparingTo("30000.00");
    }

    private static Calculation calc(DelayDeductionTerms terms, LocalDate completion) {
        return DelayDeductionCalculator.calculate(terms, CONTRACT, completion);
    }

    private static DelayDeductionTerms terms(DelayRule rule, String perDay, String pct, String fixed, int grace, String max) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("contractDeadline", DEADLINE);
        fields.put("rule", rule);
        fields.put("ratePerDay", perDay == null ? null : new BigDecimal(perDay));
        fields.put("percentPerWeek", pct == null ? null : new BigDecimal(pct));
        fields.put("fixedAmount", fixed == null ? null : new BigDecimal(fixed));
        fields.put("graceDays", grace);
        fields.put("maxDeduction", max == null ? null : new BigDecimal(max));
        return TestEntities.create(DelayDeductionTerms.class, fields);
    }
}
