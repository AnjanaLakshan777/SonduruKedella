package com.sondurukedella.security.access;

import com.sondurukedella.security.user.UserRole;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

// Approval steps (overtime, confirming commissions) belong to Super Admins and Admins.
// An "Other" role (Supervisor, Auditor, ...) can prepare the work but never approve it.
public final class Approvers {

    private static final Set<String> AUTHORITIES = Set.of(UserRole.SUPER_ADMIN.authority(), UserRole.ADMIN.authority());

    private Approvers() {
    }

    public static boolean isApprover(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(AUTHORITIES::contains);
    }
}
