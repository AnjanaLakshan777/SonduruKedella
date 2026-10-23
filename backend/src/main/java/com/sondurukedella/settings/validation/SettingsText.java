package com.sondurukedella.settings.validation;

import com.sondurukedella.common.exception.InvalidRequestException;

/** Normalises text entered in Settings forms. */
public final class SettingsText {

    private SettingsText() {
    }

    /** Trims the value; blank optional fields are stored as null. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Trims a required list entry name and checks its length against the column size. */
    public static String requireName(String value, int maxLength) {
        String name = trimToNull(value);
        if (name == null) {
            throw new InvalidRequestException("Enter a name.");
        }
        if (name.length() > maxLength) {
            throw new InvalidRequestException("The name can be at most " + maxLength + " characters.");
        }
        return name;
    }
}
