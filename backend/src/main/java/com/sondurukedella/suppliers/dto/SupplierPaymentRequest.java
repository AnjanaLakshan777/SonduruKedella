package com.sondurukedella.suppliers.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// same requestId again = no second payment
public record SupplierPaymentRequest(
        @NotNull UUID requestId,
        @NotNull(message = "Choose the payment date.") LocalDate paymentDate,
        @NotNull(message = "Enter the amount paid.") @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull(message = "Choose the payment method.") Long paymentMethodId,
        @Size(max = 100) String reference,
        String notes) {
}
