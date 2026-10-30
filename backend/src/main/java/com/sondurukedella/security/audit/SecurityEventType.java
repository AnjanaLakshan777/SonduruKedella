package com.sondurukedella.security.audit;

// keep in sync with ck_security_events_type
public enum SecurityEventType {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    LOGIN_THROTTLED,
    LOGOUT,
    USER_CREATED,
    USER_UPDATED,
    ROLE_CHANGED,
    USER_ACTIVATED,
    USER_DEACTIVATED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    SUPER_ADMIN_BOOTSTRAPPED
}
