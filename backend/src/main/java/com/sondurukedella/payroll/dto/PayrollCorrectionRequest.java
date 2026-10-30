package com.sondurukedella.payroll.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// "Record correction" on a paid week. amount is +/-, projectId null = company cost.
// same requestId again returns the first correction.
public record PayrollCorrectionRequest(
        @NotNull UUID requestId,
        @NotNull(message = "Choose the date.") LocalDate correctionDate,
        @NotNull(message = "Enter the amount.") @Digits(integer = 13, fraction = 2) BigDecimal wageDelta,
        Long projectId,
        @NotBlank(message = "Give the reason for the correction.") String reason) {
}
