package com.sondurukedella.payroll.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sondurukedella.employees.entity.AttendanceStatus;
import com.sondurukedella.payroll.service.PayrollCalculator.Cost;
import com.sondurukedella.payroll.service.PayrollCalculator.Day;
import com.sondurukedella.payroll.service.PayrollCalculator.Overtime;
import com.sondurukedella.payroll.service.PayrollCalculator.Result;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayrollCalculatorTest {

    private static final Long HOUSE = 1L;
    private static final Long SITE = 2L;

    @Test
    void halfDaysCountHalfAndBasePayIsRoundedOnce() {
        Result r = calc("3333.33", List.of(present(HOUSE), present(HOUSE), half(HOUSE), absent()), List.of(), "0", null, null);
        assertThat(r.presentDays()).isEqualTo(2);
        assertThat(r.halfDays()).isEqualTo(1);
        assertThat(r.absentDays()).isEqualTo(1);
        assertThat(r.payableDays()).isEqualByComparingTo("2.5");
        // 2.5 x 3,333.33 = 8,333.325 -> 8,333.33
        assertThat(r.basePay()).isEqualByComparingTo("8333.33");
        assertThat(r.grossPay()).isEqualByComparingTo("8333.33");
    }

    @Test
    void onlyApprovedOvertimeIsPaid() {
        Result r = calc("3500", List.of(present(HOUSE)),
                List.of(new Overtime(dec("2.5"), dec("660"), true, HOUSE), new Overtime(dec("3"), dec("660"), false, HOUSE)), "0", null, null);
        assertThat(r.overtimeHours()).isEqualByComparingTo("2.5");
        assertThat(r.pendingOvertimeHours()).isEqualByComparingTo("3");
        assertThat(r.overtimePay()).isEqualByComparingTo("1650.00");
        assertThat(r.grossPay()).isEqualByComparingTo("5150.00");
    }

    @Test
    void theProjectSplitAlwaysAddsUpToGross() {
        // half days on two projects at an awkward wage: each share rounds on its own, the residue goes to the largest
        Result r = calc("3333.33", List.of(half(HOUSE), half(SITE), half(SITE), present(null)),
                List.of(new Overtime(dec("1"), dec("416.67"), true, SITE)), "0", null, null);
        BigDecimal split = r.costs().stream().map(Cost::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(split).isEqualByComparingTo(r.grossPay());
        assertThat(r.basePay()).isEqualByComparingTo("8333.33"); // 2.5 days
        assertThat(r.costs()).extracting(Cost::projectId).containsExactly(null, HOUSE, SITE);
        assertThat(amount(r, SITE)).isEqualByComparingTo("3750.00"); // 3,333.33 for two halves + 416.67 overtime
        assertThat(amount(r, HOUSE)).isEqualByComparingTo("1666.67");
        assertThat(amount(r, null)).isEqualByComparingTo("3333.33");
    }

    @Test
    void aRoundingResidueGoesToOneShareSoNothingIsLostOrAdded() {
        // three half days on three projects: 1,666.665 rounds up three times (5,000.01) but 1.5 days is 5,000.00
        Result r = calc("3333.33", List.of(half(1L), half(2L), half(3L)), List.of(), "0", null, null);
        assertThat(r.basePay()).isEqualByComparingTo("5000.00");
        assertThat(r.costs().stream().map(Cost::amount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("5000.00");
        // equal shares: the lowest project id takes the cent
        assertThat(r.costs()).extracting(Cost::amount).usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(dec("1666.66"), dec("1666.67"), dec("1666.67"));
    }

    @Test
    void absentDaysCostNothingAndAddNoProjectShare() {
        Result r = calc("3500", List.of(absent(), absent()), List.of(), "0", null, null);
        assertThat(r.grossPay()).isZero();
        assertThat(r.costs()).isEmpty();
    }

    @Test
    void theDefaultAdvanceDeductionIsEverythingOutstandingButNeverMoreThanGross() {
        assertThat(calc("3500", List.of(present(HOUSE), present(HOUSE)), List.of(), "5000", null, null).advanceDeduction())
                .isEqualByComparingTo("5000");
        Result capped = calc("3500", List.of(present(HOUSE)), List.of(), "5000", null, null);
        assertThat(capped.advanceDeduction()).isEqualByComparingTo("3500");
        assertThat(capped.netPay()).isZero();
    }

    @Test
    void theOwnersOverrideIsCappedAtWhatIsOutstanding() {
        assertThat(calc("3500", List.of(present(HOUSE), present(HOUSE)), List.of(), "5000", "1000", null).advanceDeduction())
                .isEqualByComparingTo("1000");
        assertThat(calc("3500", List.of(present(HOUSE), present(HOUSE)), List.of(), "500", "1000", null).advanceDeduction())
                .isEqualByComparingTo("500");
        assertThat(calc("3500", List.of(present(HOUSE)), List.of(), "5000", "0", null).advanceDeduction()).isZero();
    }

    @Test
    void netIsGrossLessBothDeductionsAndCanComeOutNegative() {
        Result r = calc("3500", List.of(present(HOUSE), present(HOUSE)), List.of(), "1000", null, "500");
        assertThat(r.netPay()).isEqualByComparingTo("5500.00");
        // an other deduction bigger than what is left: payroll refuses to pay this, it is not silently capped
        assertThat(calc("3500", List.of(present(HOUSE)), List.of(), "1000", null, "3000").netPay()).isEqualByComparingTo("-500.00");
    }

    private static BigDecimal amount(Result r, Long project) {
        return r.costs().stream().filter(c -> c.isFor(project)).map(Cost::amount).findFirst().orElseThrow();
    }

    private static Result calc(String wage, List<Day> days, List<Overtime> ot, String outstanding, String override, String other) {
        return PayrollCalculator.calculate(dec(wage), days, ot, dec(outstanding), override == null ? null : dec(override),
                other == null ? null : dec(other));
    }

    private static Day present(Long project) {
        return new Day(AttendanceStatus.PRESENT, project);
    }

    private static Day half(Long project) {
        return new Day(AttendanceStatus.HALF_DAY, project);
    }

    private static Day absent() {
        return new Day(AttendanceStatus.ABSENT, null);
    }

    private static BigDecimal dec(String v) {
        return new BigDecimal(v);
    }
}
