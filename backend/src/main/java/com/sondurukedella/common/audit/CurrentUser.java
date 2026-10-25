package com.sondurukedella.common.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// The app_users id behind the current request, for created_by. Null when nobody signed in through
// the login endpoint, e.g. startup work or tests that call services directly.
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long idOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuditUser user) {
            return user.userId();
        }
        return null;
    }
}
