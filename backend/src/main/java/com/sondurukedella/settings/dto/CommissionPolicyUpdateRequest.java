package com.sondurukedella.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Percentages are exclusive of 0 and 100, as in the schema and the Settings form. */
public record CommissionPolicyUpdateRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax(value = "100", inclusive = false)
        @Digits(integer = 3, fraction = 4) BigDecimal designCommissionPool,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax(value = "100", inclusive = false)
        @Digits(integer = 3, fraction = 4) BigDecimal constructionDefaultRate,
        @NotNull Long version) {
}
