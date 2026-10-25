package com.sondurukedella.commissions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// voiding is the only correction a payment allows (the V1 trigger blocks edits and deletes)
public record CommissionPaymentVoidRequest(
        @NotBlank(message = "Give the reason the payment is being voided.") String reason,
        @NotNull Long version) {
}
