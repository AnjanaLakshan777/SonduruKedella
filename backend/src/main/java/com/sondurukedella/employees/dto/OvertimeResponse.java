package com.sondurukedella.employees.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// amount = round(hours x hourlyRate, 2): what the entry adds to the week once approved
public record OvertimeResponse(
        long id,
        long employeeId,
        String employee,
        LocalDate date,
        Long projectId,
        BigDecimal hours,
        BigDecimal hourlyRate,
        BigDecimal amount,
        boolean approved,
        String recordedBy,
        String notes,
        long version) {
}
