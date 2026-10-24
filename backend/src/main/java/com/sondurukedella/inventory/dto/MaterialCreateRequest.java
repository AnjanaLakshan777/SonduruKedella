package com.sondurukedella.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// The "New material" dialog. defaultUnitCost is a reference price only (it suggests the price on a
// receipt; FIFO layers price every movement); it defaults to 0. There is no reorder level (owner decision).
public record MaterialCreateRequest(
        @NotBlank(message = "Enter the SKU.") @Size(max = 50) String sku,
        @NotBlank(message = "Enter the material name.") @Size(max = 200) String name,
        @NotBlank(message = "Enter a category.") @Size(max = 100) String category,
        @NotNull(message = "Choose a unit.") Long unitId,
        @DecimalMin("0") @Digits(integer = 11, fraction = 4) BigDecimal defaultUnitCost) {
}
