package com.sondurukedella.inventory.dto;

import jakarta.validation.constraints.NotNull;

// Deactivating stops new receipts, issues and adjustments; history and returns are unaffected.
public record MaterialStatusRequest(@NotNull Boolean active, @NotNull Long version) {
}
