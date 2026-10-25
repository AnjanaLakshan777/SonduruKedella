package com.sondurukedella.reports.dto;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.revenue.dto.ReceivableStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

// Billed installments with what is still owed, as of asOf. Figures come from ReceivableService
// (the Revenue module's own formula); totals cover every row matching the filter, not just the page.
public record ReceivablesReport(LocalDate asOf, Totals totals, PageResponse<Row> rows) {

    public record Totals(long installments, BigDecimal net, BigDecimal received, BigDecimal writtenOff, BigDecimal outstanding,
            BigDecimal overdue, Map<AgingBucket, BigDecimal> outstandingByAging) {
    }

    // daysOverdue counts from the installment's due date to asOf, only when it is overdue.
    public record Row(
            long installmentId,
            long projectId,
            String projectCode,
            String projectName,
            long clientId,
            String clientName,
            String description,
            LocalDate billedOn,
            LocalDate dueDate,
            BigDecimal amount,
            BigDecimal deduction,
            BigDecimal net,
            BigDecimal received,
            BigDecimal writtenOff,
            BigDecimal outstanding,
            ReceivableStatus status,
            long daysOverdue,
            AgingBucket aging) {
    }

    // NOT_DUE: something outstanding but not past due. SETTLED: nothing outstanding.
    public enum AgingBucket { NOT_DUE, DAYS_1_30, DAYS_31_60, DAYS_61_90, DAYS_OVER_90, SETTLED }
}
