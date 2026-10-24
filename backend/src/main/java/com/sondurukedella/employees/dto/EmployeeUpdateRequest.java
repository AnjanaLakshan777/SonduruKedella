package com.sondurukedella.employees.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// Full replace of the profile. A new rate applies to weeks not yet paid; a paid week keeps the rate
// frozen on its payroll line. Status has its own endpoint. Send back the version you loaded.
public record EmployeeUpdateRequest(
        @NotBlank(message = "Enter the name.") @Size(max = 150) String name,
        @NotBlank(message = "Enter the role.") @Size(max = 100) String role,
        @Size(max = 30) String phone,
        @Size(max = 300) String address,
        @NotNull(message = "Enter the daily wage.") @DecimalMin(value = "0", inclusive = false, message = "Enter the daily wage.")
        @Digits(integer = 13, fraction = 2) BigDecimal dailyWage,
        @NotNull(message = "Enter the overtime rate.") @DecimalMin(value = "0", inclusive = false, message = "Enter the overtime rate.")
        @Digits(integer = 13, fraction = 2) BigDecimal overtimeRate,
        @NotNull(message = "Choose the joining date.") LocalDate joinedOn,
        @NotNull Long version) {
}
