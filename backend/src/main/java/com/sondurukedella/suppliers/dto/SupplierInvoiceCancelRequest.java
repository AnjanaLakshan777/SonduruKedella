package com.sondurukedella.suppliers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SupplierInvoiceCancelRequest(
        @NotBlank(message = "Give the reason the invoice is being cancelled.") String reason,
        @NotNull Long version) {
}
