package com.sondurukedella.security.user;

import java.util.Locale;
import java.util.regex.Pattern;

// usernames are case-insensitive, we always lower-case them
public final class Usernames {

    public static final String RULE =
            "A username is 3 to 50 characters: letters, digits, dots, dashes or underscores, starting with a letter or digit.";

    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9._-]{2,49}");

    private Usernames() {
    }

    public static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String normalized) {
        return VALID.matcher(normalized).matches();
    }
}
