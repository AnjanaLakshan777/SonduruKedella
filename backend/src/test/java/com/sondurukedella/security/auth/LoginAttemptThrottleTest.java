package com.sondurukedella.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptThrottleTest {

    private MutableClock clock;
    private LoginAttemptThrottle throttle;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-25T08:00:00Z"));
        throttle = new LoginAttemptThrottle(new LoginThrottleProperties(3, 5, Duration.ofMinutes(15)), clock);
    }

    @Test
    void attemptsAreAllowedUntilTheUsernameLimit() {
        throttle.recordFailure("kamal", "10.0.0.1");
        throttle.recordFailure("kamal", "10.0.0.2");
        assertThat(throttle.blockedFor("kamal", "10.0.0.3")).isZero();
        throttle.recordFailure("kamal", "10.0.0.3");
        assertThat(throttle.blockedFor("kamal", "10.0.0.4")).isEqualTo(Duration.ofMinutes(15));
        // other usernames from other addresses are unaffected
        assertThat(throttle.blockedFor("nimal", "10.0.0.9")).isZero();
    }

    @Test
    void theBlockEndsWithTheWindow() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("kamal", "10.0.0.1");
        }
        clock.advance(Duration.ofMinutes(10));
        assertThat(throttle.blockedFor("kamal", "10.0.0.2")).isEqualTo(Duration.ofMinutes(5));
        clock.advance(Duration.ofMinutes(5));
        assertThat(throttle.blockedFor("kamal", "10.0.0.2")).isZero();
        // and counting starts again from one
        throttle.recordFailure("kamal", "10.0.0.2");
        assertThat(throttle.blockedFor("kamal", "10.0.0.2")).isZero();
    }

    @Test
    void oneAddressTryingManyUsernamesIsBlocked() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("user" + i, "10.0.0.66");
        }
        assertThat(throttle.blockedFor("someone-new", "10.0.0.66")).isPositive();
        assertThat(throttle.blockedFor("someone-new", "10.0.0.67")).isZero();
    }

    @Test
    void aSuccessClearsTheUsernameCountButNotTheAddressCount() {
        throttle.recordFailure("kamal", "10.0.0.1");
        throttle.recordFailure("kamal", "10.0.0.1");
        throttle.recordSuccess("kamal");
        throttle.recordFailure("kamal", "10.0.0.1");
        throttle.recordFailure("kamal", "10.0.0.1");
        assertThat(throttle.blockedFor("kamal", "10.0.0.2")).isZero();
        throttle.recordFailure("kamal", "10.0.0.1");
        assertThat(throttle.blockedFor("x", "10.0.0.1")).isPositive();
    }

    @Test
    void memoryStaysBoundedWhenSomeoneSpraysRandomUsernames() {
        for (int i = 0; i < LoginAttemptThrottle.MAX_TRACKED_KEYS + 500; i++) {
            throttle.recordFailure("spray-" + i, "10.0.0.1");
        }
        // the address that did it is still tracked and blocked
        assertThat(throttle.blockedFor("anyone", "10.0.0.1")).isPositive();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
