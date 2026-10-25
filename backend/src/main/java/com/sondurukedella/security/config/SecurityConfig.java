package com.sondurukedella.security.config;

import com.sondurukedella.security.audit.SecurityAuditService;
import com.sondurukedella.security.audit.SecurityEventType;
import com.sondurukedella.security.user.AppUserPrincipal;
import com.sondurukedella.security.user.UserAccountService;
import com.sondurukedella.security.user.UserRole;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.savedrequest.NullRequestCache;

// The one place that decides who may call what. Authenticated by default; the role rules:
//   public          POST /api/v1/auth/login, GET /api/v1/auth/csrf, GET /actuator/health
//   SUPER_ADMIN     /api/v1/users/** (user administration) and the rest of /actuator
//   SUPER_ADMIN or ADMIN  every other /api/v1 endpoint (all business modules, /auth/me, logout)
//   anything else   denied
// The rules match paths only, never methods, so GET/POST/PUT/PATCH/DELETE are all covered alike.
//
// Sign-in is a server-side session (JSESSIONID cookie, HttpOnly, SameSite=Lax). CSRF protection stays
// on for every state-changing request, login and logout included, using the SPA cookie pattern:
// the XSRF-TOKEN cookie is copied into the X-XSRF-TOKEN header.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String LOGIN_URL = "/api/v1/auth/login";
    public static final String LOGOUT_URL = "/api/v1/auth/logout";
    public static final String CSRF_URL = "/api/v1/auth/csrf";
    public static final String SESSION_COOKIE = "JSESSIONID";

    private static final String SUPER_ADMIN = UserRole.SUPER_ADMIN.name();
    private static final String ADMIN = UserRole.ADMIN.name();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository, UserAccountService accounts, SecurityAuditService audit)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Spring's own error rendering for a request that already passed (or failed) the rules
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, LOGIN_URL).permitAll()
                        .requestMatchers(HttpMethod.GET, CSRF_URL).permitAll()
                        // for container and load-balancer probes; shows UP/DOWN only, no details
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole(SUPER_ADMIN)
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole(SUPER_ADMIN)
                        .requestMatchers("/api/v1/**").hasAnyRole(SUPER_ADMIN, ADMIN)
                        .anyRequest().denyAll())
                // spa() first, then our repository, so the login code rotates the same token store
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .cors(Customizer.withDefaults())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .addFilterAfter(new SessionUserFilter(accounts), SecurityContextHolderFilter.class)
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_URL)
                        .addLogoutHandler(auditLogout(audit))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(SESSION_COOKIE)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(ProblemResponses.unauthorized())
                        .accessDeniedHandler(ProblemResponses.forbidden()))
                // an API has no page to return to, and saving one would create a session for every 401
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        // the API serves JSON and attachments only; nothing may run or be framed
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    // BCrypt, with the {bcrypt} prefix so the algorithm can be upgraded later without a reset.
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Password first, account status after: a disabled account costs the same BCrypt work as any
        // other attempt, and fails with the same generic answer.
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    // The XSRF-TOKEN cookie must be readable by the app's JavaScript (that is how the pattern works);
    // it is only useful together with the session cookie, which stays HttpOnly.
    @Bean
    CsrfTokenRepository csrfTokenRepository(@Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(secureCookies).sameSite("Lax").path("/"));
        return repository;
    }

    // What the login endpoint does once the password checks out: a new session id, then a new CSRF token.
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    private static LogoutHandler auditLogout(SecurityAuditService audit) {
        return (request, response, authentication) -> {
            if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
                audit.record(SecurityEventType.LOGOUT, principal.id(), principal.id(), principal.getUsername(), null);
            }
        };
    }
}
