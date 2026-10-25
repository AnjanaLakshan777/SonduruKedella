package com.sondurukedella.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// A project's costs by category and line. Each category's amount is the sum of its lines, and total
// is the sum of the categories, to the cent. Same sources and rules as ProjectFinancialReport.Costs.
public record ProjectCostBreakdown(
        LocalDate asOf,
        ProjectRefDto project,
        List<Category> categories,
        BigDecimal total,
        // what is deliberately not a project cost here, and sources not built yet
        List<String> excluded) {

    public record Category(CostCategory category, BigDecimal amount, List<Line> lines) {
    }

    // count is the number of source records behind the line (expenses, arrangements), null when not meaningful
    public record Line(String label, BigDecimal amount, Long count) {
    }

    public enum CostCategory { MANUAL_EXPENSES, MATERIALS, LABOUR, COMMISSION }
}
