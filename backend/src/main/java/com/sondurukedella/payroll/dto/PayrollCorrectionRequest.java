package com.sondurukedella.payroll.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

// "Record correction" on a paid week: a signed change to the wages owed, with a reason, booked to a
// project (or to none, a company cost).
public record PayrollCorrectionRequest(
        @NotNull(message = "Choose the date.") LocalDate correctionDate,
        @NotNull(message = "Enter the amount.") @Digits(integer = 13, fraction = 2) BigDecimal wageDelta,
        Long projectId,
        @NotBlank(message = "Give the reason for the correction.") String reason) {
}
