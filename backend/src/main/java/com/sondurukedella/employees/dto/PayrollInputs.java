package com.sondurukedella.employees.dto;

import com.sondurukedella.employees.entity.AttendanceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// What payroll reads from this module for a date range: the days worked and the overtime. payroll
// never touches our repositories.
public record PayrollInputs(List<Day> days, List<Overtime> overtime) {

    public record Day(long employeeId, LocalDate date, AttendanceStatus status, Long projectId) {
    }

    public record Overtime(long id, long employeeId, LocalDate date, Long projectId, BigDecimal hours,
            BigDecimal hourlyRate, boolean approved) {
    }
}
