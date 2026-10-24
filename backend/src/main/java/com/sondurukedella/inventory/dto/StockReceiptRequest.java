package com.sondurukedella.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// "Record stock received": a cash purchase or opening stock. It adds stock and value only - no
// project cost and no supplier payable (credit purchases belong on a supplier invoice). requestId is
// generated once per submission; resending it returns the first receipt.
public record StockReceiptRequest(
        @NotNull UUID requestId,
        @NotNull(message = "Choose a material.") Long materialId,
        @NotNull(message = "Choose the date.") LocalDate date,
        @NotNull(message = "Enter a quantity.") @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 11, fraction = 3) BigDecimal quantity,
        @NotNull(message = "Enter the unit cost.") @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 11, fraction = 4) BigDecimal unitCost,
        Long supplierId,
        @Size(max = 100) String reference,
        String notes) {
}
