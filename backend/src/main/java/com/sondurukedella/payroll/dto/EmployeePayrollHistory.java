package com.sondurukedella.payroll.dto;

import java.math.BigDecimal;
import java.util.List;

// A worker's weeks in the range (newest first) that have any attendance, overtime or a payroll line,
// and the advances still to be recovered today.
public record EmployeePayrollHistory(
        long employeeId,
        String employee,
        BigDecimal advancesOutstanding,
        List<PayrollLineResponse> weeks) {
}
