package com.sondurukedella.security.auth;

import java.time.Duration;

// Too many failed attempts for this username or address. Mapped to HTTP 429 with Retry-After.
public class LoginThrottledException extends RuntimeException {

    private final Duration retryAfter;

    public LoginThrottledException(Duration retryAfter) {
        super("Too many failed sign-in attempts. Try again later.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
