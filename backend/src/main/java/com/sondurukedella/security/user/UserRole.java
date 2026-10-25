package com.sondurukedella.security.user;

// The only two application roles (owner decision). SUPER_ADMIN runs the system and manages user
// accounts; ADMIN uses the business modules. Spring authorities are "ROLE_" + name.
public enum UserRole {
    SUPER_ADMIN,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
