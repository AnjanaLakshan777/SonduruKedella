package com.sondurukedella.revenue.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// billedOn defaults to today (Colombo). version is the installment's, from the last read.
public record BillInstallmentRequest(LocalDate billedOn, @NotNull Long version) {
}
