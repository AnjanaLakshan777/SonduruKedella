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

// All the access rules live here. Everything needs a login by default:
// login/csrf/health are public, /users and actuator are Super Admin only, the rest of /api/v1 is
// open to both roles. Only exception: changing the commission policy is Super Admin only.
// Rules match on path, not method, so a new DELETE/PATCH can't sneak past them.
// Session cookie auth + CSRF (XSRF-TOKEN cookie -> X-XSRF-TOKEN header), login and logout included.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String LOGIN_URL = "/api/v1/auth/login";
    public static final String LOGOUT_URL = "/api/v1/auth/logout";
    public static final String CSRF_URL = "/api/v1/auth/csrf";
    public static final String SESSION_COOKIE = "JSESSIONID";
    public static final String COMMISSION_POLICY_URL = "/api/v1/settings/commission-policy";

    private static final String SUPER_ADMIN = UserRole.SUPER_ADMIN.name();
    private static final String ADMIN = UserRole.ADMIN.name();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository, UserAccountService accounts, SecurityAuditService audit)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // let Spring render its error page for requests that already went through the rules
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, LOGIN_URL).permitAll()
                        .requestMatchers(HttpMethod.GET, CSRF_URL).permitAll()
                        // for docker/LB health checks, only says UP or DOWN
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole(SUPER_ADMIN)
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole(SUPER_ADMIN)
                        // anyone can read the policy, only a Super Admin can change it
                        .requestMatchers(HttpMethod.GET, COMMISSION_POLICY_URL).hasAnyRole(SUPER_ADMIN, ADMIN)
                        .requestMatchers(COMMISSION_POLICY_URL, COMMISSION_POLICY_URL + "/**").hasRole(SUPER_ADMIN)
                        .requestMatchers("/api/v1/**").hasAnyRole(SUPER_ADMIN, ADMIN)
                        .anyRequest().denyAll())
                // spa() first, then our repo, so the login code rotates the same token store
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
                // no saved requests - otherwise every 401 would create a session
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        // we only serve JSON and file downloads
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    // {bcrypt} prefix lets us switch algorithms later without resetting passwords
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // check the password before the account status, so a disabled account takes
        // the same time and gets the same error as a wrong password
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    // JS has to read this cookie (that's how the SPA pattern works). It's useless without the
    // HttpOnly session cookie anyway.
    @Bean
    CsrfTokenRepository csrfTokenRepository(@Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(secureCookies).sameSite("Lax").path("/"));
        return repository;
    }

    // after a good login: new session id, then a new CSRF token
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
