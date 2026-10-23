package com.sondurukedella.settings.entity;

/**
 * The four fixed revenue categories. They are not stored in a table and cannot be
 * added, renamed or removed.
 */
public enum RevenueCategory {
    DESIGN("Design"),
    CONSTRUCTION("Construction"),
    CONSULTATION("Consultation"),
    OTHER_INCOME("Other Income");

    private final String displayName;

    RevenueCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
