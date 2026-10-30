package com.sondurukedella.security.auth;

import com.sondurukedella.security.user.AppUserPrincipal;
import com.sondurukedella.security.user.dto.CurrentUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Login endpoints for the React app. Flow: GET /csrf, then POST /login with the X-XSRF-TOKEN header.
// Logout is handled by Spring's logout filter. No sign-up on purpose - a Super Admin creates accounts.
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;

    // just reading the token makes the CSRF filter set the cookie
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return service.login(request, httpRequest, httpResponse);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.current(signedIn(principal));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        service.changePassword(signedIn(principal), request, httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    private static AppUserPrincipal signedIn(AppUserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Sign in through the login endpoint.");
        }
        return principal;
    }
}
