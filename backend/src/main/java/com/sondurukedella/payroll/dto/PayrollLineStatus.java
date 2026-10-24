package com.sondurukedella.payroll.dto;

// Worked out on every read, never stored.
//   DRAFT           payday hasn't come yet; attendance and overtime can still change
//   NOTHING_TO_PAY  payday reached but the week earned nothing (no paid days or approved overtime)
//   READY_TO_PAY    payday reached, something is owed and nothing is paid yet
//   PARTIALLY_PAID  some paid, some still owed
//   PAID            everything owed is paid (or the week was closed with nothing to pay)
public enum PayrollLineStatus {
    DRAFT,
    NOTHING_TO_PAY,
    READY_TO_PAY,
    PARTIALLY_PAID,
    PAID
}
