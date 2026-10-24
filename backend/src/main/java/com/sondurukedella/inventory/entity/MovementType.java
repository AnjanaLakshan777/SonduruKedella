package com.sondurukedella.inventory.entity;

// RECEIVED and RETURNED add stock, ISSUED removes it, ADJUSTMENT goes either way (count corrections,
// damage) and is the only way to correct a wrong receipt, because ledger rows are never changed.
public enum MovementType {
    RECEIVED,
    ISSUED,
    RETURNED,
    ADJUSTMENT
}
