package com.sondurukedella.payroll.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// "Record advance": cash given ahead of payday, deducted from the following paydays. Idempotent on requestId.
public record SalaryAdvanceRequest(
        @NotNull UUID requestId,
        @NotNull(message = "Choose the worker.") Long employeeId,
        @NotNull(message = "Choose the date.") LocalDate paymentDate,
        @NotNull(message = "Enter the amount given.") @DecimalMin(value = "0", inclusive = false, message = "Enter the amount given.")
        @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull(message = "Choose the payment method.") Long paymentMethodId,
        @Size(max = 100) String reference,
        String notes) {
}
