package com.sondurukedella.security.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// security.login.*: how many failed sign-ins are allowed per username and per client address
// within one window before further attempts are refused until the window ends.
@ConfigurationProperties("security.login")
public record LoginThrottleProperties(
        @DefaultValue("5") int maxFailuresPerUsername,
        @DefaultValue("20") int maxFailuresPerAddress,
        @DefaultValue("15m") Duration window) {

    public LoginThrottleProperties {
        if (maxFailuresPerUsername < 1 || maxFailuresPerAddress < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("security.login limits must be positive.");
        }
    }
}
