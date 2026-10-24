package com.sondurukedella.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PayrollPaymentResponse(
        long id,
        long employeeId,
        LocalDate weekStart,
        LocalDate paymentDate,
        BigDecimal amount,
        long paymentMethodId,
        String paymentMethod,
        String reference,
        String notes,
        boolean voided,
        OffsetDateTime voidedAt,
        String voidReason,
        UUID requestId,
        OffsetDateTime createdAt,
        long version) {
}
