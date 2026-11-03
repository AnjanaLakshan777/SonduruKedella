package com.sondurukedella.security.config;

import com.sondurukedella.security.access.ApiArea;
import com.sondurukedella.security.access.AppPage;
import com.sondurukedella.security.user.AppUserPrincipal;
import com.sondurukedella.security.user.UserAccountService;
import com.sondurukedella.security.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.util.UrlPathHelper;

// The /api/v1 rule. Super Admins pass. Admins and "Other" roles pass only when one of the pages
// ticked for them reads (GET) or changes (anything else) the endpoint's area. The pages come from
// the db on every request, so new permissions take effect at once.
final class PageAccess implements AuthorizationManager<RequestAuthorizationContext> {

    // decoded, like Spring MVC matches it, so %72eceipts can't pass as another area
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private final UserAccountService accounts;

    PageAccess(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        if (has(auth, UserRole.SUPER_ADMIN)) {
            return new AuthorizationDecision(true);
        }
        // mock users in tests carry no pages; a mock Admin keeps "every page"
        if (!(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            return new AuthorizationDecision(has(auth, UserRole.ADMIN));
        }
        HttpServletRequest request = context.getRequest();
        Optional<ApiArea> area = ApiArea.of(PATHS.getPathWithinApplication(request));
        if (area.isEmpty()) {
            return new AuthorizationDecision(false);
        }
        Set<AppPage> pages = accounts.pagesOf(principal.id());
        boolean read = HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod());
        Set<ApiArea> allowed = read ? AppPage.readable(pages) : AppPage.writable(pages);
        return new AuthorizationDecision(allowed.contains(area.get()));
    }

    private static boolean has(Authentication auth, UserRole role) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(role.authority()::equals);
    }
}
