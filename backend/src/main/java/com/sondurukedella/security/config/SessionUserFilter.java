package com.sondurukedella.security.config;

import com.sondurukedella.security.user.AppUserPrincipal;
import com.sondurukedella.security.user.SessionState;
import com.sondurukedella.security.user.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

// Checks every logged-in request against the db, so deactivating a user or changing their role
// or password kicks them out straight away instead of when the session times out.
// A user still on the starting password the Super Admin gave them can only see who they are,
// change the password or sign out - everything else is a 403 until they do.
// Not a @Component on purpose - Boot would register it a second time outside the security chain.
final class SessionUserFilter extends OncePerRequestFilter {

    private static final Set<String> OPEN_BEFORE_PASSWORD_CHANGE = Set.of(
            "/api/v1/auth/me", "/api/v1/auth/me/password", SecurityConfig.LOGOUT_URL, SecurityConfig.CSRF_URL, SecurityConfig.LOGIN_URL);
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private final UserAccountService accounts;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    SessionUserFilter(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            SessionState state = accounts.sessionState(principal.id(), principal.authVersion());
            if (state == SessionState.STALE) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                contextHolder.clearContext();
            } else if (state == SessionState.MUST_CHANGE_PASSWORD
                    && !OPEN_BEFORE_PASSWORD_CHANGE.contains(PATHS.getPathWithinApplication(request))) {
                ProblemResponses.passwordChangeRequired(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
