package com.sondurukedella.commissions.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// method is the payment method's name, which is what the frontend CommissionPayment type holds
public record CommissionPaymentResponse(
        long id,
        long commissionId,
        long projectId,
        String recipient,
        LocalDate paymentDate,
        BigDecimal amount,
        long paymentMethodId,
        String method,
        String reference,
        String notes,
        UUID requestId,
        boolean voided,
        OffsetDateTime voidedAt,
        String voidReason,
        OffsetDateTime createdAt,
        long version) {
}
