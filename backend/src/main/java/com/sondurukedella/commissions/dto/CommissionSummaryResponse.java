package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;

// drafts aren't a cost yet, so they're only counted
public record CommissionSummaryResponse(
        BigDecimal committed,
        BigDecimal paid,
        BigDecimal outstanding,
        long confirmedCount,
        long pendingCount,
        long pendingProjects,
        long voidedCount,
        long paymentCount) {
}
