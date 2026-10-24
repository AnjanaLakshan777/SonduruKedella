package com.sondurukedella.employees.dto;

import com.sondurukedella.employees.entity.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// "Save attendance" for one day: the whole register, as the attendance page sends it. A worker left
// out has no attendance that day (an existing mark is removed). Only rows that actually change are
// written, so a paid (frozen) week is refused only when you try to change it.
public record AttendanceDayRequest(@NotNull @Valid List<Entry> entries) {

    public record Entry(
            @NotNull(message = "Choose the worker.") Long employeeId,
            @NotNull(message = "Choose present, half day or absent.") AttendanceStatus status,
            Long projectId,
            String notes) {
    }
}
