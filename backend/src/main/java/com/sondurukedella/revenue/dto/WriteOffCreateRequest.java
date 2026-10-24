package com.sondurukedella.revenue.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

// Same fields as the write-off dialog. The reason rule (at least 10 characters) is the dialog's
// own and is checked in the service, after trimming.
public record WriteOffCreateRequest(
        @NotNull(message = "Choose the write-off date.") LocalDate date,
        @NotNull(message = "Enter the amount to write off.") @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull(message = "Give the reason the amount cannot be collected.") String reason) {
}
