package com.sondurukedella.revenue.entity;

// VARIATION rows ("additional work billed") come later with billing; creation only adds SCHEDULED.
public enum InstallmentKind {
    SCHEDULED,
    VARIATION
}
