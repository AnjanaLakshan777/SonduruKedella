package com.sondurukedella.commissions.entity;

// The stored status (ck_commission_arrangements_status, widened by V6). Paid / partly paid are not
// stored; they come from the payments (see CommissionView.Status).
public enum CommissionStatus {
    PENDING,
    CONFIRMED,
    VOIDED
}
