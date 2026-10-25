package com.sondurukedella.suppliers.dto;

import java.math.BigDecimal;

// For the filter applied. stockTotal is goods bought into stock (stock value, not a cost);
// projectTotal is goods delivered to projects (project material cost).
public record SupplierInvoiceSummary(long count, BigDecimal total, BigDecimal stockTotal, BigDecimal projectTotal, BigDecimal paid,
        BigDecimal outstanding, BigDecimal overdue) {
}
