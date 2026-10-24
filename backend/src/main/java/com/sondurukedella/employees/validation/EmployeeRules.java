package com.sondurukedella.employees.validation;

import com.sondurukedella.common.exception.ConflictException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.springframework.dao.DataIntegrityViolationException;

public final class EmployeeRules {

    private EmployeeRules() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // The freeze is enforced by trg_attendance_frozen / trg_overtime_entries_frozen (V1): payroll owns
    // payroll_lines, and asking it from here would make the two modules depend on each other. So we let
    // the trigger refuse the write and say it in words.
    public static RuntimeException frozenOr(DataIntegrityViolationException ex, String employeeName, LocalDate day) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause.getMessage() != null && cause.getMessage().contains("Payroll week is frozen")) {
            return new ConflictException(employeeName + "'s week of " + weekStart(day) + " has been paid, so it can't change. "
                    + "Record a payroll correction instead.");
        }
        return ex;
    }
}
