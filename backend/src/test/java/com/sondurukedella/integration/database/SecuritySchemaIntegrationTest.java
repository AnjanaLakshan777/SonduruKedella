package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// Checks the V5 constraints/triggers directly, bypassing the app. One failing statement per test
// since postgres aborts the transaction after an error.
@SpringBootTest
@Transactional
class SecuritySchemaIntegrationTest {

    private static final String INSERT_USER =
            "INSERT INTO app_users (username, display_name, email, password_hash, role) "
                    + "VALUES (?, 'X', gen_random_uuid() || '@test.local', '{bcrypt}x', ?) RETURNING id";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void onlyTheTwoApprovedRolesCanBeStored() {
        assertThatThrownBy(() -> jdbc.queryForObject(INSERT_USER, Long.class, "schema-cashier", "CASHIER"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_app_users_role");
    }

    @Test
    void aRoleMustBeGiven() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_users (username, display_name, email, password_hash) VALUES ('schema-norole', 'X', 'norole@test.local', '{bcrypt}x')"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        jdbc.queryForObject(INSERT_USER, Long.class, "schema-kamal", "ADMIN");
        assertThatThrownBy(() -> jdbc.queryForObject(INSERT_USER, Long.class, "Schema-Kamal", "ADMIN"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("uq_app_users_username_lower");
    }

    @Test
    void emailsMustBeLowerCase() {
        long id = jdbc.queryForObject(INSERT_USER, Long.class, "schema-mail", "ADMIN");
        assertThatThrownBy(() -> jdbc.update("UPDATE app_users SET email = 'Kamal@Example.com' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_app_users_email");
    }

    @Test
    void emailsMustLookLikeAnEmail() {
        long id = jdbc.queryForObject(INSERT_USER, Long.class, "schema-mail", "ADMIN");
        assertThatThrownBy(() -> jdbc.update("UPDATE app_users SET email = 'kamal' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_app_users_email");
    }

    @Test
    void emailsAreUnique() {
        long a = jdbc.queryForObject(INSERT_USER, Long.class, "schema-mail-a", "ADMIN");
        long b = jdbc.queryForObject(INSERT_USER, Long.class, "schema-mail-b", "ADMIN");
        jdbc.update("UPDATE app_users SET email = 'schema-mail@example.com' WHERE id = ?", a);
        assertThatThrownBy(() -> jdbc.update("UPDATE app_users SET email = 'schema-mail@example.com' WHERE id = ?", b))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("uq_app_users_email");
    }

    @Test
    void everyUserNeedsAnEmail() {
        long id = jdbc.queryForObject(INSERT_USER, Long.class, "schema-nomail", "ADMIN");
        assertThatThrownBy(() -> jdbc.update("UPDATE app_users SET email = NULL WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("email");
    }

    @Test
    void newUsersStartAtAuthVersionZero() {
        long id = jdbc.queryForObject(INSERT_USER, Long.class, "schema-fresh", "ADMIN");
        assertThat(jdbc.queryForObject("SELECT auth_version FROM app_users WHERE id = ?", Long.class, id)).isZero();
    }

    @Test
    void securityEventsCannotBeEdited() {
        long id = jdbc.queryForObject("INSERT INTO security_events (event_type, username) VALUES ('LOGIN_FAILED', 'x') RETURNING id", Long.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE security_events SET username = 'y' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("cannot be updated or deleted");
    }

    @Test
    void securityEventsCannotBeDeleted() {
        long id = jdbc.queryForObject("INSERT INTO security_events (event_type) VALUES ('LOGOUT') RETURNING id", Long.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM security_events WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("cannot be updated or deleted");
    }

    @Test
    void unknownSecurityEventTypesAreRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO security_events (event_type) VALUES ('PASSWORD_SHOWN')"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_security_events_type");
    }
}
