package com.sondurukedella.revenue.dto;

import com.sondurukedella.revenue.entity.InstallmentKind;
import java.math.BigDecimal;
import java.time.LocalDate;

// An installment with its money worked out. deduction is the active delay deduction on it,
// net = amount - deduction, outstanding = net - received - writtenOff (0 when not billed).
// The frontend's InstallmentView calls outstanding "remaining" and received "paid".
public record InstallmentReceivableResponse(
        long id,
        long projectId,
        String description,
        LocalDate dueDate,
        BigDecimal amount,
        InstallmentKind kind,
        boolean billed,
        LocalDate billedOn,
        BigDecimal deduction,
        BigDecimal net,
        BigDecimal received,
        BigDecimal writtenOff,
        BigDecimal outstanding,
        ReceivableStatus status,
        long daysOverdue,
        long version) {
}
