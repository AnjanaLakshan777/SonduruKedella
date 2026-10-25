package com.sondurukedella.revenue.dto;

import java.math.BigDecimal;

// For the filter applied: amount is income recorded (by income date), received and outstanding settle it.
public record OtherIncomeSummary(long count, BigDecimal amount, BigDecimal received, BigDecimal outstanding, BigDecimal overdue) {
}
