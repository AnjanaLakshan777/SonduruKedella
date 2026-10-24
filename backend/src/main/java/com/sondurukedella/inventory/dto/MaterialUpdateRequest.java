package com.sondurukedella.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Full replace of the catalogue details. The unit can only change while the material has no
// movements: after that, every quantity in the ledger is in that unit. The default cost is a reference
// price and can change at any time. Active/inactive has its own endpoint. Send back the version you loaded.
public record MaterialUpdateRequest(
        @NotBlank(message = "Enter the SKU.") @Size(max = 50) String sku,
        @NotBlank(message = "Enter the material name.") @Size(max = 200) String name,
        @NotBlank(message = "Enter a category.") @Size(max = 100) String category,
        @NotNull(message = "Choose a unit.") Long unitId,
        @DecimalMin("0") @Digits(integer = 11, fraction = 4) BigDecimal defaultUnitCost,
        @NotNull Long version) {
}
