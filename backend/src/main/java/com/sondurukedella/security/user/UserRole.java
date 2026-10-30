package com.sondurukedella.security.user;

// Only these two roles. Authorities are "ROLE_" + name.
public enum UserRole {
    SUPER_ADMIN,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
