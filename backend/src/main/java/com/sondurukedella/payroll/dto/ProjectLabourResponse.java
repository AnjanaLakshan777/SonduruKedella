package com.sondurukedella.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// A construction project's labour cost, DATABASE.md "Project costs": paid weeks use their frozen split
// (payroll_line_costs) plus corrections booked to the project; weeks not yet paid are priced live from
// attendance and approved overtime, so that part can still change. Advances and payments are never cost.
public record ProjectLabourResponse(
        long projectId,
        BigDecimal paidWeeks,
        BigDecimal corrections,
        BigDecimal openWeeks,
        BigDecimal total,
        List<Week> weeks) {

    public record Week(LocalDate weekStart, long employeeId, String employee, boolean frozen, BigDecimal amount) {
    }
}
