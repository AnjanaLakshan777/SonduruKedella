package com.sondurukedella.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// lines add up to their category, categories add up to the total
public record ProjectCostBreakdown(
        LocalDate asOf,
        ProjectRefDto project,
        List<Category> categories,
        BigDecimal total,
        List<String> excluded) {

    public record Category(CostCategory category, BigDecimal amount, List<Line> lines) {
    }

    // count = how many records the line covers, null where it doesn't make sense
    public record Line(String label, BigDecimal amount, Long count) {
    }

    public enum CostCategory { MANUAL_EXPENSES, MATERIALS, SUPPLIER_INVOICES, LABOUR, COMMISSION }
}
