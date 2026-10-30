package com.sondurukedella.security.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

// Simple brute-force protection: after too many failed logins for a username (or from one IP)
// we refuse until the window runs out, even with the right password.
// Keyed by whatever username was typed, so it doesn't leak which accounts exist.
// In-memory only - fine for one instance, resets on restart.
@Component
@EnableConfigurationProperties(LoginThrottleProperties.class)
public class LoginAttemptThrottle {

    // cap so someone spraying random usernames can't eat memory (their IP is still tracked)
    static final int MAX_TRACKED_KEYS = 10_000;

    private record Window(Instant start, int failures) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final LoginThrottleProperties limits;
    private final Clock clock;

    public LoginAttemptThrottle(LoginThrottleProperties limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
    }

    // zero = allowed now
    public Duration blockedFor(String username, String address) {
        Instant now = clock.instant();
        Duration byUsername = blockedFor(usernameKey(username), limits.maxFailuresPerUsername(), now);
        Duration byAddress = blockedFor(addressKey(address), limits.maxFailuresPerAddress(), now);
        return byUsername.compareTo(byAddress) >= 0 ? byUsername : byAddress;
    }

    public void recordFailure(String username, String address) {
        Instant now = clock.instant();
        if (windows.size() >= MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(e -> expired(e.getValue(), now));
        }
        count(usernameKey(username), now);
        count(addressKey(address), now);
    }

    // only resets the username count, not the IP
    public void recordSuccess(String username) {
        windows.remove(usernameKey(username));
    }

    private Duration blockedFor(String key, int limit, Instant now) {
        Window window = windows.get(key);
        if (window == null || expired(window, now)) {
            return Duration.ZERO;
        }
        return window.failures() >= limit ? Duration.between(now, window.start().plus(limits.window())) : Duration.ZERO;
    }

    private void count(String key, Instant now) {
        if (windows.size() >= MAX_TRACKED_KEYS && !windows.containsKey(key)) {
            return;
        }
        windows.compute(key, (k, window) -> window == null || expired(window, now)
                ? new Window(now, 1)
                : new Window(window.start(), window.failures() + 1));
    }

    private boolean expired(Window window, Instant now) {
        return !now.isBefore(window.start().plus(limits.window()));
    }

    private static String usernameKey(String username) {
        return "u:" + username;
    }

    private static String addressKey(String address) {
        return "a:" + address;
    }
}
