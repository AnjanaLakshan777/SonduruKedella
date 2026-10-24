package com.sondurukedella.revenue.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.revenue.dto.DelayTermsDraft;
import com.sondurukedella.revenue.entity.DelayRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DelayTermsRulesTest {

    private static final LocalDate DEADLINE = LocalDate.of(2027, 6, 30);
    private static final BigDecimal TEN = BigDecimal.TEN;

    @ParameterizedTest
    @ValueSource(strings = {"DESIGN", "CONSULTATION"})
    void onlyConstructionProjectsCanHaveDelayTerms(String type) {
        assertThatThrownBy(() -> DelayTermsRules.validate(type, terms(DelayRule.PER_DAY, TEN, null, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("only apply to construction");
    }

    @Test
    void eachRuleNeedsItsOwnValue() {
        assertThatCode(() -> DelayTermsRules.validate("CONSTRUCTION", terms(DelayRule.PER_DAY, TEN, null, null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> DelayTermsRules.validate("CONSTRUCTION",
                terms(DelayRule.PERCENT_PER_WEEK, null, new BigDecimal("0.5"), null))).doesNotThrowAnyException();
        assertThatCode(() -> DelayTermsRules.validate("CONSTRUCTION", terms(DelayRule.FIXED_AMOUNT, null, null, TEN)))
                .doesNotThrowAnyException();

        // a value for a different rule doesn't count
        assertThatThrownBy(() -> DelayTermsRules.validate("CONSTRUCTION", terms(DelayRule.PER_DAY, null, null, TEN)))
                .hasMessageContaining("daily amount");
        assertThatThrownBy(() -> DelayTermsRules.validate("CONSTRUCTION",
                terms(DelayRule.PERCENT_PER_WEEK, TEN, null, null))).hasMessageContaining("percentage");
        assertThatThrownBy(() -> DelayTermsRules.validate("CONSTRUCTION",
                terms(DelayRule.FIXED_AMOUNT, null, null, BigDecimal.ZERO))).hasMessageContaining("agreed amount");
    }

    @Test
    void valuesAreRejectedRatherThanRounded() {
        assertThatThrownBy(() -> DelayTermsRules.validate("CONSTRUCTION",
                terms(DelayRule.PER_DAY, new BigDecimal("15000.005"), null, null)))
                .hasMessageContaining("2 decimal places");
        assertThatThrownBy(() -> DelayTermsRules.validate("CONSTRUCTION",
                terms(DelayRule.PERCENT_PER_WEEK, null, new BigDecimal("0.12345"), null)))
                .hasMessageContaining("4 decimal places");
        assertThatCode(() -> DelayTermsRules.validate("CONSTRUCTION",
                terms(DelayRule.PERCENT_PER_WEEK, null, new BigDecimal("0.1234"), null))).doesNotThrowAnyException();
        assertThatThrownBy(() -> DelayTermsRules.validate("CONSTRUCTION",
                terms(DelayRule.PERCENT_PER_WEEK, null, new BigDecimal("1000"), null)))
                .hasMessageContaining("too large");
        assertThatThrownBy(() -> DelayTermsRules.validate("CONSTRUCTION", new DelayTermsDraft(DEADLINE,
                DelayRule.PER_DAY, TEN, null, null, null, new BigDecimal("50000.001"), null)))
                .hasMessageContaining("maximum deduction");
    }

    @Test
    void theDeadlineAndRuleAreRequired() {
        assertThatThrownBy(() -> DelayTermsRules.validate("CONSTRUCTION",
                new DelayTermsDraft(null, DelayRule.PER_DAY, TEN, null, null, null, null, null)))
                .hasMessageContaining("deadline");
        assertThatThrownBy(() -> DelayTermsRules.validate("CONSTRUCTION",
                new DelayTermsDraft(DEADLINE, null, TEN, null, null, null, null, null)))
                .hasMessageContaining("rule");
    }

    @Test
    void graceDaysAndTheCapAreChecked() {
        assertThatThrownBy(() -> DelayTermsRules.validate("CONSTRUCTION",
                new DelayTermsDraft(DEADLINE, DelayRule.PER_DAY, TEN, null, null, -1, null, null)))
                .hasMessageContaining("grace");
        assertThatThrownBy(() -> DelayTermsRules.validate("CONSTRUCTION",
                new DelayTermsDraft(DEADLINE, DelayRule.PER_DAY, TEN, null, null, 0, BigDecimal.ZERO, null)))
                .hasMessageContaining("maximum");
    }

    private static DelayTermsDraft terms(DelayRule rule, BigDecimal perDay, BigDecimal pct, BigDecimal fixed) {
        return new DelayTermsDraft(DEADLINE, rule, perDay, pct, fixed, null, null, null);
    }
}
