package com.sondurukedella.revenue.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

// method is the payment method's name, which is what the frontend ClientPayment type holds.
public record ReceiptResponse(
        long id,
        long installmentId,
        long projectId,
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
