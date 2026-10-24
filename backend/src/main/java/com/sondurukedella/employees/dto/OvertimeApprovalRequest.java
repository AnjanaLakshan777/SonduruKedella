package com.sondurukedella.employees.dto;

import jakarta.validation.constraints.NotNull;

public record OvertimeApprovalRequest(@NotNull Boolean approved, @NotNull Long version) {
}
