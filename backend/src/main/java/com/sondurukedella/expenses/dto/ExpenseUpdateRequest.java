package com.sondurukedella.expenses.dto;

import com.sondurukedella.expenses.entity.ExpenseScope;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// The "Edit expense" dialog: a full replace of the editable fields, same rules as create. Payments
// are never edited here - they have their own endpoints. Send back the version you loaded.
public record ExpenseUpdateRequest(
        @NotNull(message = "Choose the expense date.") LocalDate date,
        @NotNull(message = "Choose project or company-level.") ExpenseScope scope,
        Long projectId,
        @NotNull(message = "Choose a category.") Long categoryId,
        @NotBlank(message = "Describe what was bought or paid for.") @Size(max = 300) String description,
        @NotBlank(message = "Who was paid (or is owed)?") @Size(max = 200) String payee,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal amount,
        LocalDate dueDate,
        @Size(max = 100) String invoiceNo,
        String notes,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal quantity,
        Long unitId,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal unitPrice,
        @Size(max = 150) String machineType,
        LocalDate usageFrom,
        LocalDate usageTo,
        @Size(max = 50) String billingPeriod,
        @NotNull Long version) implements ExpenseInput {
}
