package com.sondurukedella.clients.validation;

public final class ClientText {

    private ClientText() {
    }

    // store blanks as null so we don't end up with "" and null meaning the same thing
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
