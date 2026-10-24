package com.sondurukedella.employees.dto;

import com.sondurukedella.employees.entity.EmployeeStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// The "Add employee" dialog: name, role, both rates and the joining date are required there too.
// The dialog suggests an overtime rate (daily / hours per day x multiplier), but it's the user's
// number, so it must be sent. status defaults to ACTIVE.
public record EmployeeCreateRequest(
        @NotBlank(message = "Enter the name.") @Size(max = 150) String name,
        @NotBlank(message = "Enter the role.") @Size(max = 100) String role,
        @Size(max = 30) String phone,
        @Size(max = 300) String address,
        @NotNull(message = "Enter the daily wage.") @DecimalMin(value = "0", inclusive = false, message = "Enter the daily wage.")
        @Digits(integer = 13, fraction = 2) BigDecimal dailyWage,
        @NotNull(message = "Enter the overtime rate.") @DecimalMin(value = "0", inclusive = false, message = "Enter the overtime rate.")
        @Digits(integer = 13, fraction = 2) BigDecimal overtimeRate,
        @NotNull(message = "Choose the joining date.") LocalDate joinedOn,
        EmployeeStatus status) {
}
