package com.sondurukedella.reports.dto;

import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.revenue.dto.ReceivableStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

// uses the revenue module's own numbers; totals cover all matching rows, not just this page
public record ReceivablesReport(LocalDate asOf, Totals totals, PageResponse<Row> rows) {

    public record Totals(long installments, BigDecimal net, BigDecimal received, BigDecimal writtenOff, BigDecimal outstanding,
            BigDecimal overdue, Map<AgingBucket, BigDecimal> outstandingByAging) {
    }

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

    public enum AgingBucket { NOT_DUE, DAYS_1_30, DAYS_31_60, DAYS_61_90, DAYS_OVER_90, SETTLED }
}
