package com.sondurukedella.security.user;

import com.sondurukedella.security.audit.SecurityAuditService;
import com.sondurukedella.security.audit.SecurityEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Creates the first Super Admin from env vars, but only if there's no Super Admin row at all.
// After that the vars are ignored, so a restart never resets the password. Never log the password.
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminProvisioner {

    // any number works, it just has to be the same for every instance
    private static final long BOOTSTRAP_LOCK_KEY = 0x534B_0001L;

    public enum Outcome { CREATED, ALREADY_EXISTS, NOT_CONFIGURED }

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService audit;
    private final JdbcTemplate jdbc;

    @Transactional
    public Outcome provision(SuperAdminBootstrapProperties settings) {
        // if two instances start at once, the second waits here and then sees the account exists
        jdbc.queryForObject("SELECT count(*) FROM (SELECT pg_advisory_xact_lock(?)) AS l", Long.class, BOOTSTRAP_LOCK_KEY);

        if (repository.existsByRole(UserRole.SUPER_ADMIN)) {
            if (settings.isConfigured()) {
                log.info("A Super Admin account already exists, so the bootstrap settings were ignored. "
                        + "Remove BOOTSTRAP_SUPER_ADMIN_PASSWORD from the environment.");
            }
            return Outcome.ALREADY_EXISTS;
        }
        if (!settings.isConfigured()) {
            log.warn("No Super Admin account exists and nobody can manage users. Set BOOTSTRAP_SUPER_ADMIN_USERNAME, "
                    + "BOOTSTRAP_SUPER_ADMIN_EMAIL and BOOTSTRAP_SUPER_ADMIN_PASSWORD for one start to create it "
                    + "(see docs/DEVELOPMENT_WORKFLOW.md).");
            return Outcome.NOT_CONFIGURED;
        }

        // fail the startup on a half/bad config rather than run with no admin. Don't put the values
        // in the messages.
        if (!SuperAdminBootstrapProperties.hasText(settings.username()) || !SuperAdminBootstrapProperties.hasText(settings.password())
                || !SuperAdminBootstrapProperties.hasText(settings.email())) {
            throw new IllegalStateException(
                    "Set all of BOOTSTRAP_SUPER_ADMIN_USERNAME, BOOTSTRAP_SUPER_ADMIN_EMAIL and BOOTSTRAP_SUPER_ADMIN_PASSWORD, or none.");
        }
        String username = Usernames.normalize(settings.username());
        if (!Usernames.isValid(username)) {
            throw new IllegalStateException("BOOTSTRAP_SUPER_ADMIN_USERNAME is not valid. " + Usernames.RULE);
        }
        String email = Emails.normalize(settings.email());
        if (!Emails.isValid(email)) {
            throw new IllegalStateException("BOOTSTRAP_SUPER_ADMIN_EMAIL is not valid. " + Emails.RULE);
        }
        PasswordPolicy.problem(settings.password()).ifPresent(reason -> {
            throw new IllegalStateException("BOOTSTRAP_SUPER_ADMIN_PASSWORD is not acceptable: " + reason);
        });
        // don't silently promote an existing user
        if (repository.existsByUsername(username)) {
            throw new IllegalStateException("BOOTSTRAP_SUPER_ADMIN_USERNAME is already used by an existing account. Choose another one.");
        }
        if (repository.existsByEmail(email, null)) {
            throw new IllegalStateException("BOOTSTRAP_SUPER_ADMIN_EMAIL is already used by an existing account. Choose another one.");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
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
