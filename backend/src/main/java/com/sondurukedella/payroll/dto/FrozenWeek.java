package com.sondurukedella.payroll.dto;

import java.time.LocalDate;

// a worker-week that was paid or closed: its attendance, overtime and deductions can't change any more
public record FrozenWeek(long employeeId, LocalDate weekStart) {
}
