package com.sondurukedella.commissions.dto;

// What the UI shows: the stored status, with CONFIRMED split by what has been paid (matching the
// frontend's Pending / Confirmed / Partially Paid / Paid), plus VOIDED.
public enum CommissionState {
    PENDING,
    CONFIRMED,
    PARTIALLY_PAID,
    PAID,
    VOIDED
}
