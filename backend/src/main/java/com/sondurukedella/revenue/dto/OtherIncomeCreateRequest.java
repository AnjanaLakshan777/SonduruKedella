package com.sondurukedella.revenue.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// dueDate is optional, no due date = never overdue
public record OtherIncomeCreateRequest(
        @NotNull(message = "Enter the income date.") LocalDate date,
        @NotBlank(message = "Describe the income.") @Size(max = 300) String description,
        @Size(max = 200) String payer,
        @NotNull(message = "Enter the amount.") @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal amount,
        LocalDate dueDate,
        @Size(max = 100) String reference,
        String notes) {
}
