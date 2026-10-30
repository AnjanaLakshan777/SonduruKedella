package com.sondurukedella.security.user;

import java.util.Locale;
import java.util.regex.Pattern;

// stored lower case so the unique index works. Usernames can't contain @, so anything typed at
// sign-in with an @ in it is an email.
public final class Emails {

    public static final String RULE = "Check the email address, e.g. name@example.com.";

    // same loose check as the frontend forms
    private static final Pattern VALID = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    private Emails() {
    }

    public static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String normalized) {
        return normalized.length() <= 254 && VALID.matcher(normalized).matches();
    }

    public static boolean looksLikeEmail(String value) {
        return value.indexOf('@') >= 0;
    }
}
