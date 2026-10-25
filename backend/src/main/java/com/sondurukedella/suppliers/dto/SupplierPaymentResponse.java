package com.sondurukedella.suppliers.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierPaymentResponse(
        long id,
        long invoiceId,
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
