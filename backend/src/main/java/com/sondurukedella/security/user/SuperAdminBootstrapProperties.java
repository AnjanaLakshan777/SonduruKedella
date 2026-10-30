package com.sondurukedella.security.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

// BOOTSTRAP_SUPER_ADMIN_* env vars. No defaults on purpose - nothing gets created unless they're set.
@ConfigurationProperties("security.bootstrap.super-admin")
public record SuperAdminBootstrapProperties(String username, String password, String email, String displayName) {

    boolean isConfigured() {
        return hasText(username) || hasText(password) || hasText(email);
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
