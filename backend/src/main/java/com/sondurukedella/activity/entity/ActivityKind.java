package com.sondurukedella.activity.entity;

// Mirrors ck_activity_log_kind. Only STATUS is written so far; the rest belong to modules not built yet.
public enum ActivityKind {
    PAYMENT,
    EXPENSE,
    DOCUMENT,
    STATUS,
    COMMISSION,
    NOTE,
    WRITEOFF,
    DEDUCTION
}
