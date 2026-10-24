package com.sondurukedella.expenses.dto;

import java.util.List;

// payments include voided ones (flagged) so the history is complete; they don't count toward paid
public record ExpenseDetail(ExpenseResponse expense, List<ExpensePaymentResponse> payments) {
}
