package com.sondurukedella.security.auth;

import com.sondurukedella.security.audit.SecurityAuditService;
import com.sondurukedella.security.audit.SecurityEventType;
import com.sondurukedella.security.user.AppUserPrincipal;
import com.sondurukedella.security.user.PasswordPolicy;
import com.sondurukedella.security.user.UserAccountService;
import com.sondurukedella.security.user.Usernames;
import com.sondurukedella.security.user.dto.CurrentUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;

// Login and change-password. Logout is Spring's logout filter (see SecurityConfig).
@Service
@RequiredArgsConstructor
public class AuthService {

    static final String INVALID_LOGIN = "Invalid username, email or password.";

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttemptThrottle throttle;
    private final UserAccountService accounts;
    private final SecurityAuditService audit;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public CurrentUserResponse login(LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        String username = Usernames.normalize(body.username());
        String address = request.getRemoteAddr();
        requireNotThrottled(username, address);

        Authentication result;
        try {
            // bcrypt throws on >72 bytes, just treat it as a wrong password
            if (PasswordPolicy.tooLongToHash(body.password())) {
                throw new BadCredentialsException(INVALID_LOGIN);
            }
            result = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, body.password()));
        } catch (AuthenticationException ex) {
            // same answer for unknown user / wrong password / disabled, so nobody can probe usernames
            throttle.recordFailure(username, address);
            audit.record(SecurityEventType.LOGIN_FAILED, null, null, username, null);
            throw new BadCredentialsException(INVALID_LOGIN);
        }

        throttle.recordSuccess(username);
        AppUserPrincipal principal = ((AppUserPrincipal) result.getPrincipal()).withoutPassword();
        startSession(principal, request, response);
        audit.record(SecurityEventType.LOGIN_SUCCEEDED, principal.id(), principal.id(), principal.getUsername(), null);
        return accounts.current(principal.id());
    }

    public CurrentUserResponse current(AppUserPrincipal principal) {
        return accounts.current(principal.id());
    }

    // logs out the user's other sessions, keeps this one (with a new id)
    public void changePassword(AppUserPrincipal principal, PasswordChangeRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        String address = request.getRemoteAddr();
        requireNotThrottled(principal.getUsername(), address);
        AppUserPrincipal refreshed;
        try {
            refreshed = accounts.changeOwnPassword(principal.id(), body.currentPassword(), body.newPassword());
        } catch (UserAccountService.WrongPasswordException ex) {
            // counts against the login limit too, so a stolen session can't brute force the password
            throttle.recordFailure(principal.getUsername(), address);
            throw ex;
        }
        startSession(refreshed, request, response);
    }

    private void requireNotThrottled(String username, String address) {
        Duration wait = throttle.blockedFor(username, address);
        if (!wait.isZero()) {
            audit.record(SecurityEventType.LOGIN_THROTTLED, null, null, username, null);
            throw new LoginThrottledException(wait);
        }
    }

    private void startSession(AppUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        // stops session fixation
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        // the token is lazy, touch it so this response already sends the new XSRF-TOKEN cookie
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrf) {
            csrf.getToken();
        }
    }
}
