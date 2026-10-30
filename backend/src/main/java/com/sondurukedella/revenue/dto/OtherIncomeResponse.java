package com.sondurukedella.revenue.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// received = active receipts only. status: PAID, OVERDUE, PARTIALLY_PAID or DUE (same as the UI)
public record OtherIncomeResponse(
        long id,
        LocalDate date,
        String description,
        String payer,
        BigDecimal amount,
        LocalDate dueDate,
        String reference,
        String notes,
        BigDecimal received,
        BigDecimal outstanding,
        Status status,
        OffsetDateTime createdAt,
        long version) {

    public enum Status { DUE, PARTIALLY_PAID, PAID, OVERDUE }
}
