package com.sondurukedella.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// voiding is the only correction a payment or an advance allows (the V1 triggers block edits and deletes)
public record VoidRequest(
        @NotBlank(message = "Give the reason it is being voided.") String reason,
        @NotNull Long version) {
}
