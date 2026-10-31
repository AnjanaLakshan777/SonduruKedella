package com.sondurukedella.revenue.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// One row of the schedule editor when a project is created. dueDate is the planned billing date; it
// isn't used for overdue, which counts from the day the installment is actually billed.
public record InstallmentDraft(
        @NotBlank(message = "Describe the installment.") @Size(max = 300) String description,
        @NotNull(message = "Choose a planned date.") LocalDate dueDate,
        @NotNull(message = "Enter an amount.") @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 13, fraction = 2) BigDecimal amount) {
}
