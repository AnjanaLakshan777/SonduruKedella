package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// What V1 + V6 enforce on commissions in the database itself, whatever the application does.
// Each test rolls back; one failing statement per test, because PostgreSQL aborts the transaction.
@SpringBootTest
@Transactional
class CommissionSchemaIntegrationTest {

    private static final String CONFIRMED = """
            INSERT INTO commission_arrangements (project_id, project_type, recipient_name, rate_pct, status, confirmed_on, commission_amount)
            VALUES (?, 'DESIGN', ?, 10, 'CONFIRMED', CURRENT_DATE, 1000) RETURNING id""";
    private static final String PENDING = """
            INSERT INTO commission_arrangements (project_id, project_type, recipient_name, rate_pct)
            VALUES (?, 'DESIGN', ?, 10) RETURNING id""";

    @Autowired
    private JdbcTemplate jdbc;

    private long project;

    @BeforeEach
    void setUp() {
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Schema Client', '0710000000') RETURNING id", Long.class);
        project = jdbc.queryForObject("""
                INSERT INTO projects (code, name, client_id, project_type, contract_value, start_date, expected_completion)
                VALUES ('SCHEMA-COM-1', 'Schema', ?, 'DESIGN', 10000, DATE '2026-01-01', DATE '2026-12-31') RETURNING id""", Long.class, client);
    }

    @Test
    void aConfirmedArrangementCannotBeEdited() {
        long id = jdbc.queryForObject(CONFIRMED, Long.class, project, "Nimal");
        assertThatThrownBy(() -> jdbc.update("UPDATE commission_arrangements SET commission_amount = 5 WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("cannot be changed except to void them");
    }

    @Test
    void aConfirmedArrangementCannotGoBackToDraft() {
        long id = jdbc.queryForObject(CONFIRMED, Long.class, project, "Nimal");
        assertThatThrownBy(() -> jdbc.update("UPDATE commission_arrangements SET status = 'PENDING' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void aConfirmedArrangementCannotBeDeleted() {
        long id = jdbc.queryForObject(CONFIRMED, Long.class, project, "Nimal");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM commission_arrangements WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("void them instead");
    }

    @Test
    void aDraftCanBeEditedAndDeleted() {
        long id = jdbc.queryForObject(PENDING, Long.class, project, "Nimal");
        jdbc.update("UPDATE commission_arrangements SET rate_pct = 11 WHERE id = ?", id);
        assertThat(jdbc.update("DELETE FROM commission_arrangements WHERE id = ?", id)).isOne();
    }

    @Test
    void voidingNeedsAReasonAndIsFinal() {
        long id = jdbc.queryForObject(CONFIRMED, Long.class, project, "Nimal");
        jdbc.update("UPDATE commission_arrangements SET status = 'VOIDED', voided_at = now(), void_reason = 'wrong' WHERE id = ?", id);
        assertThatThrownBy(() -> jdbc.update("UPDATE commission_arrangements SET void_reason = 'other' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void aVoidWithoutAReasonIsRefused() {
        long id = jdbc.queryForObject(CONFIRMED, Long.class, project, "Nimal");
        assertThatThrownBy(() -> jdbc.update("UPDATE commission_arrangements SET status = 'VOIDED', voided_at = now() WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_commission_arrangements_void");
    }

    @Test
    void aDraftCannotBeVoided() {
        long id = jdbc.queryForObject(PENDING, Long.class, project, "Nimal");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE commission_arrangements SET status = 'VOIDED', voided_at = now(), void_reason = 'x' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_commission_arrangements_voided_confirmed");
    }

    @Test
    void theSameRecipientAndRoleCanOnlyBeLiveOncePerProject() {
        jdbc.queryForObject(PENDING, Long.class, project, "Nimal");
        assertThatThrownBy(() -> jdbc.queryForObject(PENDING, Long.class, project, "NIMAL"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("uq_commission_arrangements_recipient_role");
    }

    @Test
    void aVoidedArrangementDoesNotBlockItsCorrection() {
        long id = jdbc.queryForObject(CONFIRMED, Long.class, project, "Nimal");
        jdbc.update("UPDATE commission_arrangements SET status = 'VOIDED', voided_at = now(), void_reason = 'wrong' WHERE id = ?", id);
        assertThat(jdbc.queryForObject(CONFIRMED, Long.class, project, "Nimal")).isNotEqualTo(id);
    }

    @Test
    void consultationProjectsCannotHaveCommission() {
        long client = jdbc.queryForObject("SELECT client_id FROM projects WHERE id = ?", Long.class, project);
        long consultation = jdbc.queryForObject("""
                INSERT INTO projects (code, name, client_id, project_type, contract_value, start_date, expected_completion)
                VALUES ('SCHEMA-COM-2', 'Consult', ?, 'CONSULTATION', 10000, DATE '2026-01-01', DATE '2026-12-31') RETURNING id""",
                Long.class, client);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO commission_arrangements (project_id, project_type, recipient_name, rate_pct)
                VALUES (?, 'CONSULTATION', 'X', 5)""", consultation)).isInstanceOf(DataAccessException.class);
    }
}
