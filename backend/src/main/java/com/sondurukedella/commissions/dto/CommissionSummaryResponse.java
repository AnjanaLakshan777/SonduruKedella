package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;

// The Commissions page KPIs. committed/paid/outstanding cover confirmed arrangements; pending ones
// are counted separately because they are not a cost yet.
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
