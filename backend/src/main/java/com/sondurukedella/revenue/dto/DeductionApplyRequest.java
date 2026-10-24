package com.sondurukedella.revenue.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

// completionDate defaults to the project's actual completion. amount defaults to what the agreed
// rule works out to; the owner may agree a smaller figure with the client, never a bigger one.
// version is the delay terms' version.
public record DeductionApplyRequest(
        LocalDate completionDate,
        @NotNull(message = "Choose the installment the deduction reduces.") Long installmentId,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal amount,
        String notes,
        @NotNull Long version) {
}
