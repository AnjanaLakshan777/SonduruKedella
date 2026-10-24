package com.sondurukedella.revenue.dto;

// Worked out on every read from the stored amounts and dates - never stored, so it can't go stale.
// The frontend's labels differ: UNBILLED = "Upcoming", BILLED = "Due", PAID = "Paid", or
// "Written Off" when writtenOff > 0.
public enum ReceivableStatus {
    UNBILLED,
    BILLED,
    PARTIALLY_PAID,
    OVERDUE,
    PAID
}
