package com.sondurukedella.security.user;

import com.sondurukedella.security.audit.SecurityAuditService;
import com.sondurukedella.security.audit.SecurityEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Creates the first Super Admin from the bootstrap settings, once. As soon as any SUPER_ADMIN row
// exists (even a disabled one) the settings are ignored: nothing is overwritten, no password is
// reset on restart, and no second "first" account appears. The password is never logged.
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminProvisioner {

    // any fixed number; it only has to be the same for every instance of this application
    private static final long BOOTSTRAP_LOCK_KEY = 0x534B_0001L;

    public enum Outcome { CREATED, ALREADY_EXISTS, NOT_CONFIGURED }

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService audit;
    private final JdbcTemplate jdbc;

    @Transactional
    public Outcome provision(SuperAdminBootstrapProperties settings) {
        // two instances starting together wait here, so only the first one creates the account
        jdbc.queryForObject("SELECT count(*) FROM (SELECT pg_advisory_xact_lock(?)) AS l", Long.class, BOOTSTRAP_LOCK_KEY);

        if (repository.existsByRole(UserRole.SUPER_ADMIN)) {
            if (settings.isConfigured()) {
                log.info("A Super Admin account already exists, so the bootstrap settings were ignored. "
                        + "Remove BOOTSTRAP_SUPER_ADMIN_PASSWORD from the environment.");
            }
            return Outcome.ALREADY_EXISTS;
        }
        if (!settings.isConfigured()) {
            log.warn("No Super Admin account exists and nobody can manage users. Set BOOTSTRAP_SUPER_ADMIN_USERNAME and "
                    + "BOOTSTRAP_SUPER_ADMIN_PASSWORD for one start to create it (see docs/DEVELOPMENT_WORKFLOW.md).");
            return Outcome.NOT_CONFIGURED;
        }

        // A half or invalid setup stops the start instead of quietly running without an administrator.
        // The messages never include the values.
        if (!SuperAdminBootstrapProperties.hasText(settings.username()) || !SuperAdminBootstrapProperties.hasText(settings.password())) {
            throw new IllegalStateException("Set both BOOTSTRAP_SUPER_ADMIN_USERNAME and BOOTSTRAP_SUPER_ADMIN_PASSWORD, or neither.");
        }
        String username = Usernames.normalize(settings.username());
        if (!Usernames.isValid(username)) {
            throw new IllegalStateException("BOOTSTRAP_SUPER_ADMIN_USERNAME is not valid. " + Usernames.RULE);
        }
        PasswordPolicy.problem(settings.password()).ifPresent(reason -> {
            throw new IllegalStateException("BOOTSTRAP_SUPER_ADMIN_PASSWORD is not acceptable: " + reason);
        });
        // never promote an existing account by accident; the operator picks a free username
        if (repository.existsByUsername(username)) {
            throw new IllegalStateException("BOOTSTRAP_SUPER_ADMIN_USERNAME is already used by an existing account. Choose another one.");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(SuperAdminBootstrapProperties.hasText(settings.displayName()) ? settings.displayName().strip() : "Super Admin");
        user.setPasswordHash(passwordEncoder.encode(settings.password()));
        user.setRole(UserRole.SUPER_ADMIN);
        AppUser saved = repository.saveAndFlush(user);
        audit.record(SecurityEventType.SUPER_ADMIN_BOOTSTRAPPED, null, saved.getId(), username, null);
        log.info("Created the first Super Admin account \"{}\". Remove BOOTSTRAP_SUPER_ADMIN_PASSWORD from the environment now.",
                username);
        return Outcome.CREATED;
    }
}
