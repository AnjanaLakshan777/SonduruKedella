package com.sondurukedella.expenses.dto;

import com.sondurukedella.expenses.entity.ExpenseScope;
import com.sondurukedella.settings.entity.ExpenseGroup;
import java.time.LocalDate;

// All optional and combined with AND. q searches description, payee and invoice number.
// from/to bound the expense date, inclusive.
public record ExpenseFilter(
        String q,
        ExpenseScope scope,
        Long projectId,
        Long categoryId,
        ExpenseGroup group,
        ExpensePaymentStatus status,
        LocalDate from,
        LocalDate to) {
}
