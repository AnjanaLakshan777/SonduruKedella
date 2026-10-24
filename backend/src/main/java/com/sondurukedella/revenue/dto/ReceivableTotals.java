package com.sondurukedella.revenue.dto;

import java.math.BigDecimal;

// revenueRecognised = sum of net over billed installments (billing is what recognises revenue).
// unbilled is the scheduled amount not billed yet: expected revenue, not revenue.
public record ReceivableTotals(
        BigDecimal billed,
        BigDecimal deductions,
        BigDecimal revenueRecognised,
        BigDecimal received,
        BigDecimal writtenOff,
        BigDecimal outstanding,
        BigDecimal overdue,
        BigDecimal unbilled) {
}
