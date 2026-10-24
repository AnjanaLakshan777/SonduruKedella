package com.sondurukedella.revenue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Voiding is the only correction a receipt allows (the V1 trigger blocks edits and deletes).
public record ReceiptVoidRequest(
        @NotBlank(message = "Give the reason the receipt is being voided.") String reason,
        @NotNull Long version) {
}
