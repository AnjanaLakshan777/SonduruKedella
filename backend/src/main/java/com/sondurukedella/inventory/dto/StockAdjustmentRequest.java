package com.sondurukedella.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// "Adjust stock": a count difference, damage, or correcting a wrong receipt (ledger rows are never
// edited). quantityChange is signed. A reduction is costed FIFO from the oldest layers, so it takes no
// unitCost. An increase is new stock with no purchase behind it, so its cost has to be stated: unitCost
// is required for it and becomes the new layer's cost (0 is allowed, but must be said). Never touches a project.
public record StockAdjustmentRequest(
        @NotNull UUID requestId,
        @NotNull(message = "Choose a material.") Long materialId,
        @NotNull(message = "Choose the date.") LocalDate date,
        @NotNull(message = "Enter a quantity.") @Digits(integer = 11, fraction = 3) BigDecimal quantityChange,
        @NotBlank(message = "Give a reason for the adjustment (count difference, damage...).") String reason,
        @Size(max = 100) String reference,
        @DecimalMin(value = "0", message = "The unit cost can't be negative.") @Digits(integer = 11, fraction = 4) BigDecimal unitCost) {
}
