package com.sondurukedella.expenses.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sondurukedella.expenses.dto.ExpensePaymentStatus;
import com.sondurukedella.expenses.service.ExpenseCalculator.Balance;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExpenseCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    @Test
    void nothingPaidIsUnpaidUntilTheDueDatePasses() {
        assertThat(of("50000", "0", null).status()).isEqualTo(ExpensePaymentStatus.UNPAID);
        assertThat(of("50000", "0", TODAY).status()).isEqualTo(ExpensePaymentStatus.UNPAID); // due today: not late yet
        Balance late = of("50000", "0", TODAY.minusDays(1));
        assertThat(late.status()).isEqualTo(ExpensePaymentStatus.OVERDUE);
        assertThat(late.outstanding()).isEqualByComparingTo("50000.00");
    }

    @Test
    void partPaidIsPartiallyPaidButOverdueWins() {
        assertThat(of("50000", "20000", TODAY.plusDays(3)).status()).isEqualTo(ExpensePaymentStatus.PARTIALLY_PAID);
        assertThat(of("50000", "20000", null).outstanding()).isEqualByComparingTo("30000.00");
        assertThat(of("50000", "20000", TODAY.minusDays(3)).status()).isEqualTo(ExpensePaymentStatus.OVERDUE);
    }

    @Test
    void paidInFullIsPaidEvenIfItWasLate() {
        Balance paid = of("50000", "50000", TODAY.minusDays(30));
        assertThat(paid.status()).isEqualTo(ExpensePaymentStatus.PAID);
        assertThat(paid.outstanding()).isEqualByComparingTo("0");
    }

    @Test
    void theOutstandingNeverGoesNegativeAndKeepsTwoDecimals() {
        // can only happen through bad data, but must not show a negative debt
        assertThat(of("100", "120", null).outstanding()).isEqualByComparingTo("0");
        assertThat(of("1000", "333.33", null).outstanding().scale()).isEqualTo(2);
    }

    private static Balance of(String amount, String paid, LocalDate due) {
        return ExpenseCalculator.of(new BigDecimal(amount), new BigDecimal(paid), due, TODAY);
    }
}
