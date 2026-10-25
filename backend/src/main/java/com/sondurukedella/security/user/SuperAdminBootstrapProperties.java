package com.sondurukedella.security.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

// security.bootstrap.super-admin.* (env BOOTSTRAP_SUPER_ADMIN_USERNAME, _PASSWORD, _DISPLAY_NAME).
// Read only while no Super Admin exists yet; there are no defaults, so nothing is created unless
// the operator sets them.
@ConfigurationProperties("security.bootstrap.super-admin")
public record SuperAdminBootstrapProperties(String username, String password, String displayName) {

    boolean isConfigured() {
        return hasText(username) || hasText(password);
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
