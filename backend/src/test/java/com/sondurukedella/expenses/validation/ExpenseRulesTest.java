package com.sondurukedella.expenses.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.expenses.dto.ExpenseCreateRequest;
import com.sondurukedella.expenses.entity.ExpenseScope;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExpenseRulesTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 20);

    @Test
    void aMaterialsLineWorksOutItsOwnAmount() {
        // 12.5 cubes x 4,333.3333 = 54,166.66625 -> 54,166.67 (HALF_UP, like the V1 check's round())
        assertThat(ExpenseRules.amount(materials("12.5", 3L, "4333.3333", null))).isEqualByComparingTo("54166.67");
        assertThat(ExpenseRules.amount(materials("12.5", 3L, "4333.3333", "54166.67"))).isEqualByComparingTo("54166.67");
    }

    @Test
    void aMaterialsAmountThatDisagreesIsRejected() {
        assertThatThrownBy(() -> ExpenseRules.amount(materials("10", 3L, "100", "999.99")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("LKR 1,000.00");
    }

    @Test
    void aMaterialsLineNeedsAllThreeParts() {
        assertThatThrownBy(() -> ExpenseRules.amount(materials("10", null, "100", null)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("together");
        assertThatThrownBy(() -> ExpenseRules.amount(materials(null, 3L, null, "100")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void aTinyMaterialsLineThatRoundsToNothingIsRejected() {
        assertThatThrownBy(() -> ExpenseRules.amount(materials("0.001", 3L, "0.0001", null)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("more than zero");
    }

    @Test
    void anOrdinaryExpenseNeedsAnAmount() {
        assertThatThrownBy(() -> ExpenseRules.amount(plain(ExpenseScope.COMPANY, null, null)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("Enter the amount");
        assertThat(ExpenseRules.amount(plain(ExpenseScope.COMPANY, null, "1500.5"))).isEqualByComparingTo("1500.50");
    }

    @Test
    void scopeAndProjectMustAgree() {
        assertThatThrownBy(() -> ExpenseRules.validateScope(plain(ExpenseScope.PROJECT, null, "100")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("Choose the project");
        assertThatThrownBy(() -> ExpenseRules.validateScope(plain(ExpenseScope.COMPANY, 4L, "100")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("not assigned to a project");
        assertThatCode(() -> ExpenseRules.validateScope(plain(ExpenseScope.PROJECT, 4L, "100"))).doesNotThrowAnyException();
        assertThatCode(() -> ExpenseRules.validateScope(plain(ExpenseScope.COMPANY, null, "100"))).doesNotThrowAnyException();
    }

    @Test
    void aRentalPeriodCannotEndBeforeItStarts() {
        assertThatThrownBy(() -> ExpenseRules.validatePeriod(rental(DATE, DATE.minusDays(1))))
                .isInstanceOf(InvalidRequestException.class);
        assertThatCode(() -> ExpenseRules.validatePeriod(rental(DATE, DATE))).doesNotThrowAnyException();
        assertThatCode(() -> ExpenseRules.validatePeriod(rental(DATE, null))).doesNotThrowAnyException();
    }

    private static ExpenseCreateRequest materials(String qty, Long unit, String price, String amount) {
        return new ExpenseCreateRequest(DATE, ExpenseScope.PROJECT, 4L, 1L, "River sand", "Hardware", dec(amount), null, null,
                null, dec(qty), unit, dec(price), null, null, null, null, null);
    }

    private static ExpenseCreateRequest plain(ExpenseScope scope, Long projectId, String amount) {
        return new ExpenseCreateRequest(DATE, scope, projectId, 1L, "Banner", "Print shop", dec(amount), null, null, null,
                null, null, null, null, null, null, null, null);
    }

    private static ExpenseCreateRequest rental(LocalDate from, LocalDate to) {
        return new ExpenseCreateRequest(DATE, ExpenseScope.PROJECT, 4L, 1L, "Excavator", "Hire Co", dec("1000"), null, null,
                null, null, null, null, "JCB 3CX", from, to, null, null);
    }

    private static BigDecimal dec(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
