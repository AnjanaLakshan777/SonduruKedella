package com.sondurukedella.commissions.dto;

// CONFIRMED split by payments, same as the UI badges
public enum CommissionState {
    PENDING,
    CONFIRMED,
    PARTIALLY_PAID,
    PAID,
    VOIDED
}
