package com.sondurukedella.security.user;

public enum SessionState {
    CURRENT,
    // user disabled, or role/password changed since this session signed in
    STALE,
    // signed in with the starting password; only the password change is open
    MUST_CHANGE_PASSWORD
}
