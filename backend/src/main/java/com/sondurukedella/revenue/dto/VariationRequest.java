package com.sondurukedella.revenue.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// "Additional work billed" - billedOn defaults to today
public record VariationRequest(
        @NotBlank(message = "Describe the additional work.") @Size(max = 300) String description,
        @NotNull(message = "Enter the amount.") @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull(message = "Choose the due date.") LocalDate dueDate,
        LocalDate billedOn) {
}
