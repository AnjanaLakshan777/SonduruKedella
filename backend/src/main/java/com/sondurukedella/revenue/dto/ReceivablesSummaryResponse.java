package com.sondurukedella.revenue.dto;

import java.math.BigDecimal;

// The KPI strip on the Receivables tab, across all projects. partiallyPaid is the outstanding
// part of installments that have had some receipt or write-off (overdue ones included), as the UI counts it.
public record ReceivablesSummaryResponse(
        BigDecimal outstanding,
        BigDecimal overdue,
        BigDecimal partiallyPaid,
        BigDecimal writtenOff,
        long openInstallments,
        long overdueInstallments,
        long partiallyPaidInstallments) {
}
