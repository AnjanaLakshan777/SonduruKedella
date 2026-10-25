package com.sondurukedella.security.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

// Slows down password guessing. Counts failed sign-ins (and wrong "current password" entries) per
// typed username and per client address in fixed windows; at the limit, further attempts are
// refused until the window ends, whether or not the password is right.
//
// Keyed by the typed username, not the account, so unknown usernames are throttled the same way and
// the answer says nothing about which accounts exist. In memory: fine for this single-instance
// app, but it resets on restart and isn't shared between instances.
@Component
@EnableConfigurationProperties(LoginThrottleProperties.class)
public class LoginAttemptThrottle {

    // a bound on memory if someone sprays random usernames; past it, new keys aren't tracked, but the
    // sprayer's own address key already is
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

    // how long until another attempt is allowed; zero when it is allowed now
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

    // a correct password clears the username's count; the address count stays
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
