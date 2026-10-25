package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// What V5 enforces in the database itself, whatever the application does. Each test rolls back;
// one failing statement per test, because PostgreSQL aborts the transaction after an error.
@SpringBootTest
@Transactional
class SecuritySchemaIntegrationTest {

    private static final String INSERT_USER =
            "INSERT INTO app_users (username, display_name, password_hash, role) VALUES (?, 'X', '{bcrypt}x', ?) RETURNING id";

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
                "INSERT INTO app_users (username, display_name, password_hash) VALUES ('schema-norole', 'X', '{bcrypt}x')"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        jdbc.queryForObject(INSERT_USER, Long.class, "schema-kamal", "ADMIN");
        assertThatThrownBy(() -> jdbc.queryForObject(INSERT_USER, Long.class, "Schema-Kamal", "ADMIN"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("uq_app_users_username_lower");
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
