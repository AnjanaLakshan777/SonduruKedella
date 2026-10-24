package com.sondurukedella.suppliers.validation;

public final class SupplierText {

    private SupplierText() {
    }

    // store blanks as null so "" and null don't mean two different things
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
