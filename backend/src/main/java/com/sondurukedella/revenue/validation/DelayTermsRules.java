package com.sondurukedella.revenue.validation;

import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.money.Money;
import com.sondurukedella.revenue.dto.DelayTermsDraft;
import com.sondurukedella.revenue.entity.DelayRule;
import java.math.BigDecimal;

public final class DelayTermsRules {

    // the only project type the delay_deduction_terms table accepts
    public static final String CONSTRUCTION = "CONSTRUCTION";

    // percent_per_week is NUMERIC(7,4); this is the largest value that column can hold
    private static final int PERCENT_SCALE = 4;
    private static final BigDecimal MAX_PERCENT = new BigDecimal("999.9999");

    private DelayTermsRules() {
    }

    public static void validate(String projectType, DelayTermsDraft terms) {
        // Rejected rather than ignored: silently dropping terms the user filled in would lose
        // part of the agreement without anyone noticing.
        if (!CONSTRUCTION.equals(projectType)) {
            throw new InvalidRequestException("Delay deduction terms only apply to construction projects.");
        }
        if (terms.contractDeadline() == null) {
            throw new InvalidRequestException("Set the deadline from the agreement.");
        }
        if (terms.rule() == null) {
            throw new InvalidRequestException("Choose the agreed deduction rule.");
        }
        BigDecimal value = switch (terms.rule()) {
            case PER_DAY -> terms.ratePerDay();
            case PERCENT_PER_WEEK -> terms.percentPerWeek();
            case FIXED_AMOUNT -> terms.fixedAmount();
        };
        if (value == null || value.signum() <= 0) {
            throw new InvalidRequestException(switch (terms.rule()) {
                case PER_DAY -> "Enter the agreed daily amount.";
                case PERCENT_PER_WEEK -> "Enter the agreed percentage.";
                case FIXED_AMOUNT -> "Enter the agreed amount.";
            });
        }
        // Same "reject, don't round" rule as the schedule. Postgres would otherwise quietly round a
        // percentage with more than 4 decimals to fit NUMERIC(7,4).
        int maxScale = terms.rule() == DelayRule.PERCENT_PER_WEEK ? PERCENT_SCALE : Money.SCALE;
        if (value.stripTrailingZeros().scale() > maxScale) {
            throw new InvalidRequestException("The deduction value can have at most " + maxScale + " decimal places.");
        }
        if (terms.rule() == DelayRule.PERCENT_PER_WEEK && value.compareTo(MAX_PERCENT) > 0) {
            throw new InvalidRequestException("The percentage is too large.");
        }
        if (terms.maxDeduction() != null && terms.maxDeduction().stripTrailingZeros().scale() > Money.SCALE) {
            throw new InvalidRequestException("The maximum deduction can have at most 2 decimal places.");
        }
        if (terms.graceDays() != null && terms.graceDays() < 0) {
            throw new InvalidRequestException("The grace period cannot be negative.");
        }
        if (terms.maxDeduction() != null && terms.maxDeduction().signum() <= 0) {
            throw new InvalidRequestException("The maximum deduction must be greater than zero, or left empty.");
        }
    }
}
