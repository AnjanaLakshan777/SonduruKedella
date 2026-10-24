package com.sondurukedella.employees.dto;

import com.sondurukedella.employees.entity.EmployeeStatus;
import jakarta.validation.constraints.NotNull;

// Inactive workers drop off the attendance register and the pick lists. Their history, and any week
// not yet paid, stay and can still be paid.
public record EmployeeStatusRequest(@NotNull EmployeeStatus status, @NotNull Long version) {
}
