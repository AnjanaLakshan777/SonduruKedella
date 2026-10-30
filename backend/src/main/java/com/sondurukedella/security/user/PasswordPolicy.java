package com.sondurukedella.security.user;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

// Same rules everywhere a password gets set. Max is 72 bytes because that's all bcrypt uses.
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    // empty = ok
    public static Optional<String> problem(String password) {
        if (password == null || password.isBlank()) {
            return Optional.of("Enter a password.");
        }
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            return Optional.of("The password must be at least " + MIN_LENGTH + " characters.");
        }
        if (tooLongToHash(password)) {
            return Optional.of("The password is too long (at most " + MAX_BYTES + " bytes).");
        }
        return Optional.empty();
    }

    public static boolean tooLongToHash(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
