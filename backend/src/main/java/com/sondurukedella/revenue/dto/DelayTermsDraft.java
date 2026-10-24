package com.sondurukedella.revenue.dto;

import com.sondurukedella.revenue.entity.DelayRule;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

// The "Delay Deduction" card on Create Project. Only the value for the chosen rule is
// required; the service drops the other two (the form keeps old values when you switch rule).
public record DelayTermsDraft(
        @NotNull(message = "Set the deadline from the agreement.") LocalDate contractDeadline,
        @NotNull DelayRule rule,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal ratePerDay,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 3, fraction = 4) BigDecimal percentPerWeek,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal fixedAmount,
        @Min(0) Integer graceDays,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal maxDeduction,
        String notes) {
}
