package com.sondurukedella.commissions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Voiding a confirmed arrangement: it stops being a cost and payable, and stays in the history.
public record CommissionVoidRequest(
        @NotBlank(message = "Give the reason the commission is being voided.") @Size(max = 500) String reason,
        @NotNull Long version) {
}
