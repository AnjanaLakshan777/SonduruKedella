package com.sondurukedella.employees.dto;

import com.sondurukedella.employees.entity.AttendanceStatus;
import java.time.LocalDate;

public record AttendanceResponse(
        long id,
        long employeeId,
        String employee,
        LocalDate date,
        AttendanceStatus status,
        Long projectId,
        String notes,
        long version) {
}
