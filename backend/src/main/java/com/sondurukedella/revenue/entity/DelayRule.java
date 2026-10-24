package com.sondurukedella.revenue.entity;

// Each rule uses exactly one value column (see ck_delay_deduction_terms_rule).
public enum DelayRule {
    PER_DAY,
    PERCENT_PER_WEEK,
    FIXED_AMOUNT
}
