package com.sondurukedella.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// "Return from project", always against the original issue. The material and project come from that
// issue; materialId/projectId are optional and, when sent, must match it - a cheap guard against a
// form that has the wrong issue selected.
public record StockReturnRequest(
        @NotNull UUID requestId,
        @NotNull(message = "Choose the issue the material is coming back from.") Long issueId,
        Long materialId,
        Long projectId,
        @NotNull(message = "Choose the date.") LocalDate date,
        @NotNull(message = "Enter a quantity.") @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 11, fraction = 3) BigDecimal quantity,
        @Size(max = 100) String reference,
        String notes) {
}
