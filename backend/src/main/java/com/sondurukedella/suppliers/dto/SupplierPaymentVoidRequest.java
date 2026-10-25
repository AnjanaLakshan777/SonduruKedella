package com.sondurukedella.suppliers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SupplierPaymentVoidRequest(
        @NotBlank(message = "Give the reason the payment is being voided.") String reason,
        @NotNull Long version) {
}
