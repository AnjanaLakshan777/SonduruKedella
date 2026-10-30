package com.sondurukedella.common.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// user id for created_by, or null when nobody is logged in (startup jobs, service-level tests)
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
