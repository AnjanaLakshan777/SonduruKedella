package com.sondurukedella.revenue.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// requestId is generated once per form submission (crypto.randomUUID() in the browser). Sending
// the same one again - a double click, a retry after a timeout - returns the first receipt
// instead of recording the money twice.
public record ReceiptCreateRequest(
        @NotNull UUID requestId,
        @NotNull(message = "Choose the payment date.") LocalDate paymentDate,
        @NotNull(message = "Enter the amount received.") @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull(message = "Choose the payment method.") Long paymentMethodId,
        @Size(max = 100) String reference,
        String notes) {
}
