package com.sondurukedella.revenue.dto;

import java.math.BigDecimal;

// amount by income date; received/outstanding settle it
public record OtherIncomeSummary(long count, BigDecimal amount, BigDecimal received, BigDecimal outstanding, BigDecimal overdue) {
}
