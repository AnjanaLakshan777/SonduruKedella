package com.sondurukedella.revenue.dto;

import com.sondurukedella.revenue.entity.DelayRule;
import java.math.BigDecimal;
import java.time.LocalDate;

// Mirrors delay_deduction_terms. Only the value for the chosen rule is set; the other two are null.
// The applied* fields and actualCompletion stay null until applying deductions is built - they're
// returned anyway so the shape matches the frontend DelayTerms type.
public record DelayTermsResponse(
        long id,
        long projectId,
        boolean enabled,
        LocalDate contractDeadline,
        DelayRule rule,
        BigDecimal ratePerDay,
        BigDecimal percentPerWeek,
        BigDecimal fixedAmount,
        int graceDays,
        BigDecimal maxDeduction,
        String notes,
        LocalDate actualCompletion,
        BigDecimal appliedAmount,
        LocalDate appliedOn,
        Long appliedToInstallmentId,
        long version) {
}
