package com.sondurukedella.employees.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// "Record overtime". hourlyRate defaults to the worker's overtime rate, which the form pre-fills and
// the supervisor may change. Entries start unapproved; only approved hours are paid.
public record OvertimeCreateRequest(
        @NotNull(message = "Choose the worker.") Long employeeId,
        @NotNull(message = "Choose the date.") LocalDate date,
        Long projectId,
        @NotNull(message = "Enter 0.5 to 8 hours.") @DecimalMin(value = "0", inclusive = false, message = "Enter 0.5 to 8 hours.")
        @DecimalMax(value = "8", message = "Enter 0.5 to 8 hours.") @Digits(integer = 2, fraction = 2) BigDecimal hours,
        @DecimalMin(value = "0", inclusive = false, message = "Enter the hourly rate.") @Digits(integer = 13, fraction = 2) BigDecimal hourlyRate,
        @NotBlank(message = "Say who recorded the overtime.") @Size(max = 150) String recordedBy,
        String notes) {
}
