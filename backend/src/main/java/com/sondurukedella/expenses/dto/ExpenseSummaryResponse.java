package com.sondurukedella.expenses.dto;

import java.math.BigDecimal;

// The KPI strip on the Expenses page, for whatever filter is applied. Covers manual expenses only:
// labour, commission, supplier invoices and stock issues come from their own modules.
public record ExpenseSummaryResponse(
        long count,
        BigDecimal total,
        BigDecimal projectTotal,
        BigDecimal companyTotal,
        BigDecimal paid,
        BigDecimal outstanding,
        BigDecimal overdueOutstanding) {
}
