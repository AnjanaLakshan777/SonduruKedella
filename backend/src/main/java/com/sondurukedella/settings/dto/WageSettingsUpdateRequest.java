package com.sondurukedella.settings.dto;

import com.sondurukedella.settings.entity.Payday;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Limits follow the wage_settings constraints in V1. */
public record WageSettingsUpdateRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2)
        BigDecimal defaultDailyRate,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("12")
        @Digits(integer = 2, fraction = 2) BigDecimal hoursPerDay,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 3, fraction = 2)
        BigDecimal overtimeMultiplier,
        @NotNull Payday payday,
        @NotNull Long version) {
}
