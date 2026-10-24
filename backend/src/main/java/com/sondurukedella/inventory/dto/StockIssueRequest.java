package com.sondurukedella.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// "Issue to project". The cost is never sent: it is worked out FIFO from the oldest stock at the moment of
// posting, and it is fixed on the movement so later purchase prices don't change it.
public record StockIssueRequest(
        @NotNull UUID requestId,
        @NotNull(message = "Choose a material.") Long materialId,
        @NotNull(message = "Choose the project receiving the material.") Long projectId,
        @NotNull(message = "Choose the date.") LocalDate date,
        @NotNull(message = "Enter a quantity.") @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 11, fraction = 3) BigDecimal quantity,
        @Size(max = 100) String reference,
        String notes) {
}
