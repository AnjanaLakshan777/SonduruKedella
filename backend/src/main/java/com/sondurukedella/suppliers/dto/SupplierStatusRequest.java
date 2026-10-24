package com.sondurukedella.suppliers.dto;

import jakarta.validation.constraints.NotNull;

// Deactivating hides the supplier from new expenses; everything already recorded keeps it.
public record SupplierStatusRequest(@NotNull Boolean active, @NotNull Long version) {
}
