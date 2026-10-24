package com.sondurukedella.expenses.dto;

// Worked out on every read from the amount, the active payments, the due date and today (Colombo).
// Same four states and order as the frontend's PayStatus for expenses: Paid, Overdue, Partially Paid, Unpaid.
public enum ExpensePaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    OVERDUE,
    PAID
}
