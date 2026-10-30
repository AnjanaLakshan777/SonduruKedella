package com.sondurukedella.suppliers.dto;

import java.math.BigDecimal;

// stockTotal = bought into stock (not a cost), projectTotal = delivered to sites (a cost)
public record SupplierInvoiceSummary(long count, BigDecimal total, BigDecimal stockTotal, BigDecimal projectTotal, BigDecimal paid,
        BigDecimal outstanding, BigDecimal overdue) {
}
