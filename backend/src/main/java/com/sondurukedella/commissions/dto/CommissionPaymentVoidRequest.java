package com.sondurukedella.commissions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CommissionPaymentVoidRequest(
        @NotBlank(message = "Give the reason the payment is being voided.") String reason,
        @NotNull Long version) {
}
