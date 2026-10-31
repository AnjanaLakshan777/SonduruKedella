package com.sondurukedella.revenue.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.revenue.dto.InstallmentDraft;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleRulesTest {

    private static final LocalDate DUE = LocalDate.of(2026, 12, 1);

    @Test
    void aScheduleThatAddsUpExactlyIsAccepted() {
        assertThatCode(() -> ScheduleRules.validateInitialSchedule(new BigDecimal("1000000.00"),
                List.of(row("Advance", "300000"), row("Roof", "450000.50"), row("Handover", "249999.50"))))
                .doesNotThrowAnyException();
    }

    @Test
    void differentScalesOfTheSameAmountStillMatch() {
        assertThatCode(() -> ScheduleRules.validateInitialSchedule(new BigDecimal("1500"),
                List.of(row("All", "1500.0")))).doesNotThrowAnyException();
    }

    @Test
    void evenOneCentOffIsRejected() {
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(new BigDecimal("1000.00"),
                List.of(row("A", "500.00"), row("B", "499.99"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("999.99").hasMessageContaining("1000.00");
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(new BigDecimal("1000.00"),
                List.of(row("A", "500.00"), row("B", "500.01"))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void aContractValueNeedsASchedule() {
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(new BigDecimal("1000"), List.of()))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(new BigDecimal("1000"), null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void noFeeMeansNoSchedule() {
        assertThatCode(() -> ScheduleRules.validateInitialSchedule(BigDecimal.ZERO, List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> ScheduleRules.validateInitialSchedule(BigDecimal.ZERO, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(BigDecimal.ZERO, List.of(row("Fee", "5000"))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void incompleteRowsAreRejected() {
        BigDecimal contract = new BigDecimal("1000");
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(contract,
                List.of(new InstallmentDraft(" ", DUE, contract)))).hasMessageContaining("Describe installment 1");
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(contract,
                List.of(new InstallmentDraft("All", null, contract)))).hasMessageContaining("planned date");
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(contract,
                List.of(row("A", "1000"), new InstallmentDraft("B", DUE, null)))).hasMessageContaining("Installment 2");
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(contract,
                Arrays.asList(row("A", "1000"), null))).hasMessageContaining("Installment 2 is empty");
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(contract,
                List.of(row("A", "1000"), row("B", "0")))).hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(contract,
                List.of(row("x".repeat(301), "1000")))).hasMessageContaining("too long");
    }

    @Test
    void moreThanTwoDecimalsIsRejectedNotRounded() {
        assertThatThrownBy(() -> ScheduleRules.validateInitialSchedule(new BigDecimal("1000"),
                List.of(row("A", "500.005"), row("B", "499.995"))))
                .hasMessageContaining("2 decimal places");
    }

    private static InstallmentDraft row(String description, String amount) {
        return new InstallmentDraft(description, DUE, new BigDecimal(amount));
    }
}
