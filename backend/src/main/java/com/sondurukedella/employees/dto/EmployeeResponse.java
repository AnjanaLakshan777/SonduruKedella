package com.sondurukedella.employees.dto;

import com.sondurukedella.employees.entity.EmployeeStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record EmployeeResponse(
        long id,
        String name,
        String role,
        String phone,
        String address,
        BigDecimal dailyWage,
        BigDecimal overtimeRate,
        EmployeeStatus status,
        LocalDate joinedOn,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version) {
}
