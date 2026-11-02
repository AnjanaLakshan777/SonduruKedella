package com.sondurukedella.employees.dto;

import java.time.LocalDate;

public record HolidayResponse(LocalDate date, String note) {
}
