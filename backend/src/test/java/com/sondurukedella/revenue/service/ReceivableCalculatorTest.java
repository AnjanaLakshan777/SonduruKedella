package com.sondurukedella.revenue.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sondurukedella.revenue.dto.ReceivableStatus;
import com.sondurukedella.revenue.service.ReceivableCalculator.Receivable;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReceivableCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    void anUnbilledInstallmentIsNotAReceivable() {
        Receivable r = of("100000", false, TODAY.minusDays(30), ZERO, ZERO, ZERO);

        assertThat(r.status()).isEqualTo(ReceivableStatus.UNBILLED);
        assertThat(r.outstanding()).isEqualByComparingTo("0"); // however old its planned date
        assertThat(r.daysOverdue()).isZero();
    }

    @Test
    void billedTodayAndUnpaidIsAlreadyOverdue() {
        Receivable r = of("100000", true, TODAY, ZERO, ZERO, ZERO);

        assertThat(r.status()).isEqualTo(ReceivableStatus.OVERDUE); // owed from the billing day itself
        assertThat(r.daysOverdue()).isZero();
        assertThat(r.outstanding()).isEqualByComparingTo("100000.00");
    }

    @Test
    void aPartPaymentDoesNotStopItBeingOverdue() {
        assertThat(of("100000", true, TODAY, ZERO, big("40000"), ZERO).status()).isEqualTo(ReceivableStatus.OVERDUE);
        // only a billing date still ahead (never set by the app) gives the in-between statuses
        assertThat(of("100000", true, TODAY.plusDays(1), ZERO, ZERO, ZERO).status()).isEqualTo(ReceivableStatus.BILLED);
        assertThat(of("100000", true, TODAY.plusDays(1), ZERO, big("40000"), ZERO).status()).isEqualTo(ReceivableStatus.PARTIALLY_PAID);
        Receivable late = of("100000", true, TODAY.minusDays(3), ZERO, big("40000"), ZERO);
        assertThat(late.status()).isEqualTo(ReceivableStatus.OVERDUE);
        assertThat(late.daysOverdue()).isEqualTo(3);
        assertThat(late.outstanding()).isEqualByComparingTo("60000.00");
    }

    @Test
    void daysOverdueCountFromTheBillingDayWithNoSeparateDueDate() {
        Receivable dayAfter = of("100000", true, TODAY.minusDays(1), ZERO, ZERO, ZERO);
        assertThat(dayAfter.status()).isEqualTo(ReceivableStatus.OVERDUE);
        assertThat(dayAfter.daysOverdue()).isEqualTo(1);

        Receivable monthOld = of("100000", true, TODAY.minusDays(30), ZERO, ZERO, ZERO);
        assertThat(monthOld.daysOverdue()).isEqualTo(30);
    }

    @Test
    void aWriteOffAlsoCountsAsSettlementButIsNotReceived() {
        Receivable r = of("100000", true, TODAY, ZERO, ZERO, big("30000"));

        assertThat(r.status()).isEqualTo(ReceivableStatus.OVERDUE); // the rest is still owed
        assertThat(r.partlySettled()).isTrue();
        assertThat(r.received()).isEqualByComparingTo("0");
        assertThat(r.writtenOff()).isEqualByComparingTo("30000.00");
        assertThat(r.outstanding()).isEqualByComparingTo("70000.00");
    }

    @Test
    void settledInFullIsPaidWhetherByReceiptsOrWriteOffsAndEvenWhenLate() {
        assertThat(of("100000", true, TODAY.minusDays(9), ZERO, big("100000"), ZERO).status()).isEqualTo(ReceivableStatus.PAID);
        assertThat(of("100000", true, TODAY.minusDays(9), ZERO, big("60000"), big("40000")).status())
                .isEqualTo(ReceivableStatus.PAID);
    }

    @Test
    void theDelayDeductionReducesTheNetAndTheOutstanding() {
        // the docs' example: 2,000,000 billed, 150,000 deducted, 1,500,000 received -> 350,000 owed
        Receivable r = of("2000000", true, TODAY.minusDays(1), big("150000"), big("1500000"), ZERO);

        assertThat(r.net()).isEqualByComparingTo("1850000.00");
        assertThat(r.outstanding()).isEqualByComparingTo("350000.00");
        assertThat(r.status()).isEqualTo(ReceivableStatus.OVERDUE);
    }

    @Test
    void theOutstandingNeverGoesBelowZero() {
        // e.g. a receipt recorded before a deduction was applied
        Receivable r = of("100000", true, TODAY, big("30000"), big("80000"), ZERO);

        assertThat(r.outstanding()).isEqualByComparingTo("0");
        assertThat(r.status()).isEqualTo(ReceivableStatus.PAID);
    }

    @Test
    void amountsCarryTwoDecimals() {
        Receivable r = of("1000", true, TODAY, ZERO, big("333.33"), ZERO);
        assertThat(r.outstanding()).isEqualByComparingTo("666.67");
        assertThat(r.outstanding().scale()).isEqualTo(2);
    }

    private static Receivable of(String amount, boolean billed, LocalDate billedOn, BigDecimal deduction,
            BigDecimal received, BigDecimal writtenOff) {
        return ReceivableCalculator.of(big(amount), billed, billedOn, deduction, received, writtenOff, TODAY);
    }

    private static BigDecimal big(String value) {
        return new BigDecimal(value);
    }
}
