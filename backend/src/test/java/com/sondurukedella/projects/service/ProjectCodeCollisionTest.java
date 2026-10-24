package com.sondurukedella.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.sondurukedella.projects.entity.Project;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.repository.ProjectRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// The retry depends on recognising Postgres' real error for uq_projects_code, so check it
// against the database rather than a hand-built exception.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectCodeCollisionTest {

    @Autowired
    private ProjectRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aDuplicateCodeIsRecognisedAsACollision() {
        long client = client();
        repository.saveAndFlush(project("PRJ-2026-001", client, "2026-02-01"));

        DataIntegrityViolationException ex = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(project("PRJ-2026-001", client, "2026-02-01")));

        assertThat(ProjectCodes.isCodeCollision(ex)).isTrue();
    }

    @Test
    void otherConstraintFailuresAreNotCollisions() {
        long client = client();

        DataIntegrityViolationException ex = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(project("PRJ-2026-001", client, "2026-01-01")));

        assertThat(ProjectCodes.isCodeCollision(ex)).isFalse();
    }

    private long client() {
        return jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Code Test', '0710000000') RETURNING id",
                Long.class);
    }

    private static Project project(String code, long clientId, String end) {
        return new Project(code, "Code test", clientId, ProjectType.DESIGN, new BigDecimal("1000"),
                LocalDate.of(2026, 1, 1), LocalDate.parse(end), ProjectStatus.PLANNING);
    }
}
