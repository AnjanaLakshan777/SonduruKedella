package com.sondurukedella.projects.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.projects.dto.ProjectFilter;
import com.sondurukedella.projects.entity.Project;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

// Real Postgres schema from Flyway. Point it at a throwaway DB - each test rolls back, but the
// assertions expect no other projects to exist.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectRepositoryTest {

    private static final ProjectFilter NONE = filter(null);

    @Autowired
    private ProjectRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private long bandara;
    private long trust;

    @BeforeEach
    void setUp() {
        // plain SQL for clients: this test is about the projects table, not the clients module
        bandara = client("K. M. Bandara");
        trust = client("Rajarata Educational Trust");
        save("PRJ-2026-001", "Medawachchiya Single-Storey House", bandara, ProjectType.CONSTRUCTION, "4500000",
                "2026-03-01", "2026-12-15", ProjectStatus.IN_PROGRESS, "Medawachchiya");
        save("PRJ-2026-002", "Kekirawa School Library Block", trust, ProjectType.CONSTRUCTION, "9800000",
                "2026-07-20", "2027-06-30", ProjectStatus.PLANNING, "Kekirawa");
        save("PRJ-2026-003", "Jaya Mawatha House Plan & 3D", bandara, ProjectType.DESIGN, "350000",
                "2026-07-10", "2026-09-30", ProjectStatus.COMPLETED, "Jaya Mawatha, Anuradhapura");
        save("PRJ-2027-001", "Site Report 50%_Check", trust, ProjectType.CONSULTATION, "0",
                "2027-01-05", "2027-02-05", ProjectStatus.ON_HOLD, null);
    }

    @Test
    void searchMatchesNameCodeAndLocationIgnoringCase() {
        assertThat(codes(filter("LIBRARY"), List.of())).containsExactly("PRJ-2026-002");
        assertThat(codes(filter("prj-2026-00"), List.of())).containsExactly("PRJ-2026-001", "PRJ-2026-002", "PRJ-2026-003");
        assertThat(codes(filter("anuradhapura"), List.of())).containsExactly("PRJ-2026-003");
    }

    @Test
    void searchAlsoMatchesTheClientIdsTheCallerLookedUp() {
        // as if ClientService.findIdsByName("bandara") returned this id
        assertThat(codes(filter("bandara"), List.of(bandara))).containsExactly("PRJ-2026-001", "PRJ-2026-003");
        assertThat(codes(filter("bandara"), List.of())).isEmpty();
    }

    @Test
    void wildcardsInTheSearchAreLiteral() {
        assertThat(codes(filter("50%_"), List.of())).containsExactly("PRJ-2027-001");
        assertThat(codes(filter("%"), List.of())).containsExactly("PRJ-2027-001");
    }

    @Test
    void filtersByTypeStatusAndClient() {
        assertThat(codes(new ProjectFilter(null, ProjectType.CONSTRUCTION, null, null, null, null, null, null), List.of()))
                .containsExactly("PRJ-2026-001", "PRJ-2026-002");
        assertThat(codes(new ProjectFilter(null, null, ProjectStatus.COMPLETED, null, null, null, null, null), List.of()))
                .containsExactly("PRJ-2026-003");
        assertThat(codes(new ProjectFilter(null, null, null, trust, null, null, null, null), List.of()))
                .containsExactly("PRJ-2026-002", "PRJ-2027-001");
    }

    @Test
    void dateRangesAreInclusive() {
        LocalDate jul10 = LocalDate.of(2026, 7, 10);
        assertThat(codes(new ProjectFilter(null, null, null, null, jul10, LocalDate.of(2026, 7, 20), null, null), List.of()))
                .containsExactly("PRJ-2026-002", "PRJ-2026-003");
        // "Due in 2027", the way the UI's preset will be sent
        assertThat(codes(new ProjectFilter(null, null, null, null, null, null,
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)), List.of()))
                .containsExactly("PRJ-2026-002", "PRJ-2027-001");
        assertThat(codes(new ProjectFilter(null, null, null, null, null, null, null, LocalDate.of(2026, 9, 30)), List.of()))
                .containsExactly("PRJ-2026-003");
    }

    @Test
    void sortingAndPagingAreStable() {
        Sort byValue = Sort.by(Sort.Order.desc("contractValue"), Sort.Order.asc("id"));
        List<String> first = repository.findAll(ProjectSpecifications.matching(NONE, List.of()), PageRequest.of(0, 2, byValue))
                .map(Project::getCode).getContent();
        List<String> second = repository.findAll(ProjectSpecifications.matching(NONE, List.of()), PageRequest.of(1, 2, byValue))
                .map(Project::getCode).getContent();

        assertThat(first).containsExactly("PRJ-2026-002", "PRJ-2026-001");
        assertThat(second).containsExactly("PRJ-2026-003", "PRJ-2027-001");
    }

    @Test
    void codesAreFoundByYearPrefix() {
        assertThat(repository.findCodesStartingWith("PRJ-2026-%"))
                .containsExactlyInAnyOrder("PRJ-2026-001", "PRJ-2026-002", "PRJ-2026-003");
    }

    @Test
    void theAdvisoryLockRunsOnPostgres() {
        assertThat(repository.lockCodeYear(1001, 2026)).isEqualTo(1);
        // re-entrant within the same transaction
        assertThat(repository.lockCodeYear(1001, 2026)).isEqualTo(1);
    }

    // V1 constraints, one failing statement per test because Postgres aborts the transaction after an error.

    @Test
    void theDatabaseRejectsADuplicateCode() {
        assertThatThrownBy(() -> save("PRJ-2026-001", "Copy", bandara, ProjectType.DESIGN, "1000",
                "2026-01-01", "2026-02-01", ProjectStatus.PLANNING, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAZeroContractForDesignWork() {
        assertThatThrownBy(() -> save("PRJ-2026-010", "Free design", bandara, ProjectType.DESIGN, "0",
                "2026-01-01", "2026-02-01", ProjectStatus.PLANNING, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsCompletionOnOrBeforeTheStart() {
        assertThatThrownBy(() -> save("PRJ-2026-011", "Same day", bandara, ProjectType.DESIGN, "1000",
                "2026-01-01", "2026-01-01", ProjectStatus.PLANNING, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAnUnknownClient() {
        assertThatThrownBy(() -> save("PRJ-2026-012", "Nobody's", Long.MAX_VALUE, ProjectType.DESIGN, "1000",
                "2026-01-01", "2026-02-01", ProjectStatus.PLANNING, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsDelayTermsOnADesignProjectEvenWithAFakeTypeCopy() {
        long design = projectId("PRJ-2026-003");
        // claiming CONSTRUCTION doesn't help: the composite FK checks it against the real project
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO delay_deduction_terms (project_id, project_type, contract_deadline, rule, rate_per_day)
                VALUES (?, 'CONSTRUCTION', DATE '2026-09-30', 'PER_DAY', 1000)""", design))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsDelayTermsWithoutTheValueTheirRuleNeeds() {
        long construction = projectId("PRJ-2026-001");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO delay_deduction_terms (project_id, project_type, contract_deadline, rule, fixed_amount)
                VALUES (?, 'CONSTRUCTION', DATE '2026-12-15', 'PER_DAY', 1000)""", construction))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAZeroInstallment() {
        long construction = projectId("PRJ-2026-001");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO installments (project_id, description, due_date, amount) VALUES (?, 'x', DATE '2026-05-01', 0)",
                construction)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> codes(ProjectFilter filter, Collection<Long> clientIds) {
        return repository.findAll(ProjectSpecifications.matching(filter, clientIds), Sort.by("code"))
                .stream().map(Project::getCode).toList();
    }

    private static ProjectFilter filter(String q) {
        return new ProjectFilter(q, null, null, null, null, null, null, null);
    }

    private long client(String name) {
        return jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES (?, '0710000000') RETURNING id",
                Long.class, name);
    }

    private long projectId(String code) {
        return jdbc.queryForObject("SELECT id FROM projects WHERE code = ?", Long.class, code);
    }

    private void save(String code, String name, long clientId, ProjectType type, String value, String start,
            String end, ProjectStatus status, String location) {
        Project project = new Project(code, name, clientId, type, new BigDecimal(value), LocalDate.parse(start),
                LocalDate.parse(end), status);
        project.setLocation(location);
        repository.saveAndFlush(project);
    }
}
