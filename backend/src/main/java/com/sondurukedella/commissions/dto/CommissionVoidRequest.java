package com.sondurukedella.commissions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommissionVoidRequest(
        @NotBlank(message = "Give the reason the commission is being voided.") @Size(max = 500) String reason,
        @NotNull Long version) {
}
