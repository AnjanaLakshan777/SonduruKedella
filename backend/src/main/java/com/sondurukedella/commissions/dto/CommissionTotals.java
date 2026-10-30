package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;

// committed = confirmed only; pendingAmount = drafts (not a cost yet)
public record CommissionTotals(BigDecimal committed, BigDecimal paid, BigDecimal outstanding, BigDecimal pendingAmount) {
}
