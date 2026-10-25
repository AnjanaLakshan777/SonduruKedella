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

// Re-checks a signed-in session against the database on every request (one primary-key lookup).
// When the account was deactivated, or its role or password changed since this session signed in,
// the session is invalidated and the request continues as anonymous, so protected endpoints answer
// 401. Not a bean on purpose: Spring Boot would also register a Filter bean outside the chain.
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
