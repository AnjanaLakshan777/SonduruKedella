package com.sondurukedella.commissions.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// One recipient row of the project's Commissions tab. id and version are sent for rows that already
// exist; a row without an id is new. role defaults to "Commission".
public record CommissionRowRequest(
        Long id,
        Long version,
        @NotBlank(message = "Enter the recipient's name.") @Size(max = 150) String recipient,
        @Size(max = 100) String role,
        @NotNull(message = "Enter the recipient's percentage.")
        @DecimalMin(value = "0", inclusive = false, message = "Enter a percentage between 0 and 100.")
        @DecimalMax(value = "100", inclusive = false, message = "Enter a percentage between 0 and 100.")
        @Digits(integer = 2, fraction = 4) BigDecimal ratePct) {
}
