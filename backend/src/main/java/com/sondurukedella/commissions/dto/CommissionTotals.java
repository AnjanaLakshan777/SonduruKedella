package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;

// committed is the commission cost: confirmed arrangements only (drafts and voids are not costs).
// paid and outstanding settle it; pendingAmount is the draft preview, not yet a cost.
public record CommissionTotals(BigDecimal committed, BigDecimal paid, BigDecimal outstanding, BigDecimal pendingAmount) {
}
