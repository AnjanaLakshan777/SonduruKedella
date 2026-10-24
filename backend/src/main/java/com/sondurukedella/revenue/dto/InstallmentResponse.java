package com.sondurukedella.revenue.dto;

import com.sondurukedella.revenue.entity.InstallmentKind;
import java.math.BigDecimal;
import java.time.LocalDate;

// Field names follow the frontend Installment type. billed is just billedOn != null - V1 has no
// separate flag. Paid, remaining and overdue need client payments, which aren't built yet, so
// they're left out rather than guessed.
public record InstallmentResponse(
        long id,
        long projectId,
        String description,
        LocalDate dueDate,
        BigDecimal amount,
        InstallmentKind kind,
        boolean billed,
        LocalDate billedOn,
        long version) {
}
