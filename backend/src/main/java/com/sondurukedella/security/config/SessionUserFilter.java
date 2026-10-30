package com.sondurukedella.security.config;

import com.sondurukedella.security.user.AppUserPrincipal;
import com.sondurukedella.security.user.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

// Checks every logged-in request against the db, so deactivating a user or changing their role
// or password kicks them out straight away instead of when the session times out.
// Not a @Component on purpose - Boot would register it a second time outside the security chain.
final class SessionUserFilter extends OncePerRequestFilter {

    private final UserAccountService accounts;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    SessionUserFilter(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                && !accounts.isSessionCurrent(principal.id(), principal.authVersion())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            contextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
