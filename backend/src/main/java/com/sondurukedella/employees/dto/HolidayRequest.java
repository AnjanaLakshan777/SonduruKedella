package com.sondurukedella.employees.dto;

import jakarta.validation.constraints.Size;

// note is optional, e.g. "Poya day"
public record HolidayRequest(@Size(max = 200) String note) {
}
