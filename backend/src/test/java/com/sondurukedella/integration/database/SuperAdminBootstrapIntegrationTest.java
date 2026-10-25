package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.security.user.AppUser;
import com.sondurukedella.security.user.AppUserRepository;
import com.sondurukedella.security.user.SuperAdminBootstrapProperties;
import com.sondurukedella.security.user.SuperAdminProvisioner;
import com.sondurukedella.security.user.SuperAdminProvisioner.Outcome;
import com.sondurukedella.security.user.UserRole;
import com.sondurukedella.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

// The one-time first Super Admin. Each test starts from "no Super Admin exists" (any existing ones
// are demoted inside the test transaction) and rolls back. The passwords here are test-only.
@SpringBootTest
@Transactional
class SuperAdminBootstrapIntegrationTest {

    private static final String BOOT_PASSWORD = "test-only-bootstrap-1";

    @Autowired
    private SuperAdminProvisioner provisioner;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void noSuperAdminYet() {
        jdbc.update("UPDATE app_users SET role = 'ADMIN' WHERE role = 'SUPER_ADMIN'");
    }

    @Test
    void withoutSettingsNothingIsCreated() {
        long before = count();
        assertThat(provisioner.provision(new SuperAdminBootstrapProperties(null, null, null))).isEqualTo(Outcome.NOT_CONFIGURED);
        assertThat(provisioner.provision(new SuperAdminBootstrapProperties(" ", "", null))).isEqualTo(Outcome.NOT_CONFIGURED);
        assertThat(count()).isEqualTo(before);
    }

    @Test
    void theFirstSuperAdminIsCreatedOnceAndNeverOverwritten() {
        String username = "Owner-" + System.nanoTime();
        assertThat(provisioner.provision(new SuperAdminBootstrapProperties(" " + username + " ", BOOT_PASSWORD, "Owner")))
                .isEqualTo(Outcome.CREATED);

        AppUser created = users.findByUsername(username.toLowerCase()).orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getDisplayName()).isEqualTo("Owner");
        assertThat(created.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(BOOT_PASSWORD);
        assertThat(encoder.matches(BOOT_PASSWORD, created.getPasswordHash())).isTrue();
        String hash = created.getPasswordHash();

        // a restart with the settings still present, or changed, does nothing
        assertThat(provisioner.provision(new SuperAdminBootstrapProperties(username, "a-different-password-9", null)))
                .isEqualTo(Outcome.ALREADY_EXISTS);
        assertThat(provisioner.provision(new SuperAdminBootstrapProperties("someone-else", BOOT_PASSWORD, null)))
                .isEqualTo(Outcome.ALREADY_EXISTS);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_users WHERE id = ?", String.class, created.getId())).isEqualTo(hash);
        assertThat(superAdmins()).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM security_events WHERE event_type = 'SUPER_ADMIN_BOOTSTRAPPED' AND target_user_id = ?",
                Long.class, created.getId())).isOne();
    }

    @Test
    void aDisabledSuperAdminStillCountsSoTheBootstrapDoesNotRunAgain() {
        AppUser existing = TestUsers.create(users, encoder, "old-root", UserRole.SUPER_ADMIN);
        existing.setEnabled(false);
        users.saveAndFlush(existing);
        assertThat(provisioner.provision(new SuperAdminBootstrapProperties("new-root", BOOT_PASSWORD, null)))
                .isEqualTo(Outcome.ALREADY_EXISTS);
        assertThat(users.findByUsername("new-root")).isEmpty();
    }

    @Test
    void halfOrInvalidSettingsStopTheStartWithoutRevealingThePassword() {
        assertThatThrownBy(() -> provisioner.provision(new SuperAdminBootstrapProperties("owner-1", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("both");
        assertThatThrownBy(() -> provisioner.provision(new SuperAdminBootstrapProperties(null, BOOT_PASSWORD, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(BOOT_PASSWORD);
        assertThatThrownBy(() -> provisioner.provision(new SuperAdminBootstrapProperties("owner-1", "too-short", null)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("too-short");
        assertThatThrownBy(() -> provisioner.provision(new SuperAdminBootstrapProperties("bad name!", BOOT_PASSWORD, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(BOOT_PASSWORD);
        assertThat(superAdmins()).isZero();
    }

    @Test
    void anExistingAccountIsNeverPromotedByTheBootstrap() {
        AppUser admin = TestUsers.create(users, encoder, "taken", UserRole.ADMIN);
        assertThatThrownBy(() -> provisioner.provision(new SuperAdminBootstrapProperties(admin.getUsername(), BOOT_PASSWORD, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(encoder.matches(TestUsers.PASSWORD, users.findById(admin.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    private long count() {
        return jdbc.queryForObject("SELECT count(*) FROM app_users", Long.class);
    }

    private long superAdmins() {
        return jdbc.queryForObject("SELECT count(*) FROM app_users WHERE role = 'SUPER_ADMIN'", Long.class);
    }
}
