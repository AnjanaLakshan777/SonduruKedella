package com.sondurukedella.payroll.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

// The owner's overrides for a worker's week. advanceDeduction null = deduct the default (everything
// outstanding, up to the week's gross); 0 = deduct nothing this week. otherDeduction defaults to 0.
// version: the one you loaded, or null when the week has no adjustment yet.
public record PayrollAdjustmentRequest(
        @DecimalMin(value = "0", message = "A deduction can't be negative.") @Digits(integer = 13, fraction = 2) BigDecimal advanceDeduction,
        @DecimalMin(value = "0", message = "A deduction can't be negative.") @Digits(integer = 13, fraction = 2) BigDecimal otherDeduction,
        String otherNote,
        Long version) {
}
