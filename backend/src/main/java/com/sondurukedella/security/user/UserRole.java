package com.sondurukedella.security.user;

// SUPER_ADMIN opens every page and manages users. ADMIN and CUSTOM open the pages ticked for
// that user; CUSTOM also carries a name of its own (Supervisor, Auditor, ...).
// Authorities are "ROLE_" + name.
public enum UserRole {
    SUPER_ADMIN,
    ADMIN,
    CUSTOM;

    public String authority() {
        return "ROLE_" + name();
    }
}
