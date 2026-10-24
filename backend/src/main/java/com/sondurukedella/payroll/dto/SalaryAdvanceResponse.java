package com.sondurukedella.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// recovered = what paid weeks have deducted from it; outstanding = amount - recovered (0 once voided)
public record SalaryAdvanceResponse(
        long id,
        long employeeId,
        String employee,
        LocalDate paymentDate,
        BigDecimal amount,
        long paymentMethodId,
        String paymentMethod,
        String reference,
        String notes,
        BigDecimal recovered,
        BigDecimal outstanding,
        boolean voided,
        OffsetDateTime voidedAt,
        String voidReason,
        UUID requestId,
        long version) {
}
