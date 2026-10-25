package com.sondurukedella.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sondurukedella.activity.entity.ActivityKind;
import com.sondurukedella.activity.service.ActivityService;
import com.sondurukedella.clients.dto.ClientCreateRequest;
import com.sondurukedella.clients.entity.ClientKind;
import com.sondurukedella.projects.dto.ProjectCreateRequest;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.dto.ProjectStatusUpdateRequest;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.service.ProjectService;
import com.sondurukedella.revenue.dto.DelayTermsDraft;
import com.sondurukedella.revenue.dto.InstallmentDraft;
import com.sondurukedella.revenue.entity.DelayRule;
import com.sondurukedella.revenue.service.DelayTermsService;
import com.sondurukedella.revenue.service.InstallmentScheduleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.test.web.servlet.MockMvc;

// Deliberately NOT @Transactional: a test-managed transaction would roll everything back anyway
// and hide whether our own transactions do. These commit for real, so run them against a throwaway
// DB only. Everything they create is named "TxTest ..." (years 2091+) and deleted afterwards.
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class ProjectTransactionIntegrationTest {

    private static final String MARK = "TxTest ";
    private static final List<String> TABLES =
            List.of("clients", "projects", "installments", "delay_deduction_terms", "activity_log");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private InstallmentScheduleService scheduleService;

    @Autowired
    private DelayTermsService delayTermsService;

    @MockitoSpyBean
    private ActivityService activityService;

    private Map<String, Long> before;

    @BeforeEach
    void countRows() {
        before = rowCounts();
    }

    @AfterEach
    void cleanUp() {
        String projects = "SELECT id FROM projects WHERE name LIKE '" + MARK + "%'";
        jdbc.update("DELETE FROM activity_log WHERE project_id IN (" + projects + ")");
        jdbc.update("DELETE FROM delay_deduction_terms WHERE project_id IN (" + projects + ")");
        jdbc.update("DELETE FROM installments WHERE project_id IN (" + projects + ")");
        jdbc.update("DELETE FROM projects WHERE name LIKE '" + MARK + "%'");
        jdbc.update("DELETE FROM clients WHERE name LIKE '" + MARK + "%'");
    }

    @Test
    void aSuccessfulCreateCommitsEveryPart() {
        projectService.create(construction(newClient("Committed"), delayTerms(), "2091-03-01"));

        Map<String, Long> after = rowCounts();
        assertThat(after.get("clients")).isEqualTo(before.get("clients") + 1);
        assertThat(after.get("projects")).isEqualTo(before.get("projects") + 1);
        assertThat(after.get("installments")).isEqualTo(before.get("installments") + 2);
        assertThat(after.get("delay_deduction_terms")).isEqualTo(before.get("delay_deduction_terms") + 1);
        assertThat(after.get("activity_log")).isEqualTo(before.get("activity_log") + 1);
    }

    @Test
    void aBadScheduleRollsBackTheInlineClientAndProject() throws Exception {
        // the client and project are written before Revenue rejects the total, so this checks real rollback
        mockMvc.perform(post("/api/v1/projects").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"TxTest Bad schedule","type":"CONSTRUCTION",
                         "newClient":{"name":"TxTest Orphan","kind":"INDIVIDUAL","phone":"0710000000"},
                         "contractValue":1000000,"startDate":"2091-01-01","expectedCompletion":"2091-12-01",
                         "schedule":[{"description":"Advance","dueDate":"2091-01-01","amount":400000}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("must match exactly")));

        assertThat(rowCounts()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clients WHERE name = 'TxTest Orphan'", Long.class)).isZero();
    }

    @Test
    void delayTermsOnADesignProjectRollBackEverything() throws Exception {
        mockMvc.perform(post("/api/v1/projects").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"TxTest Design with terms","type":"DESIGN",
                         "newClient":{"name":"TxTest Orphan","kind":"INDIVIDUAL","phone":"0710000000"},
                         "contractValue":100000,"startDate":"2091-01-01","expectedCompletion":"2091-03-01",
                         "schedule":[{"description":"Fee","dueDate":"2091-01-01","amount":100000}],
                         "delayTerms":{"contractDeadline":"2091-03-01","rule":"FIXED_AMOUNT","fixedAmount":5000}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("only apply to construction")));

        assertThat(rowCounts()).isEqualTo(before);
    }

    @Test
    void aFailureAtTheLastStepRollsBackClientProjectScheduleAndTerms() {
        // activity is written last, after the schedule and delay terms
        failActivityLog();

        assertThatThrownBy(() -> projectService.create(construction(newClient("Last step"), delayTerms(), "2091-05-01")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(rowCounts()).isEqualTo(before);
    }

    @Test
    void aStatusChangeRollsBackIfItsActivityEntryFails() {
        ProjectResponse project = projectService.create(construction(newClient("Status"), null, "2091-06-01"));
        failActivityLog();

        assertThatThrownBy(() -> projectService.changeStatus(project.id(),
                new ProjectStatusUpdateRequest(ProjectStatus.COMPLETED, project.version())))
                .isInstanceOf(IllegalStateException.class);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, actual_completion, version FROM projects WHERE id = ?", project.id());
        assertThat(row.get("status")).isEqualTo("PLANNING");
        assertThat(row.get("actual_completion")).isNull();
        assertThat(row.get("version")).isEqualTo(0L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_log WHERE project_id = ?", Long.class,
                project.id())).isEqualTo(1L);
    }

    @Test
    void scheduleTermsAndActivityCannotBeWrittenOutsideAProjectTransaction() {
        // there's no surrounding transaction here, so MANDATORY must refuse before anything is written
        assertThatThrownBy(() -> scheduleService.createInitialSchedule(1L, new BigDecimal("1000"),
                List.of(new InstallmentDraft("Fee", LocalDate.of(2091, 1, 1), new BigDecimal("1000")))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> delayTermsService.createTerms(1L, "CONSTRUCTION", delayTerms()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> activityService.record(1L, ActivityKind.NOTE, "orphan"))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(rowCounts()).isEqualTo(before);
    }

    @Test
    void concurrentCreatesGetDistinctSequentialCodes() throws Exception {
        long clientId = jdbc.queryForObject(
                "INSERT INTO clients (name, phone) VALUES ('" + MARK + "Busy', '0710000000') RETURNING id", Long.class);
        int threads = 8;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                ProjectCreateRequest request = design(clientId, "2092-0" + (i % 9 + 1) + "-01");
                results.add(pool.submit(() -> {
                    go.await();
                    return projectService.create(request).code();
                }));
            }
            go.countDown();
            List<String> codes = new ArrayList<>();
            for (Future<String> result : results) {
                codes.add(result.get(60, TimeUnit.SECONDS));
            }

            assertThat(codes).containsExactlyInAnyOrderElementsOf(
                    IntStream.rangeClosed(1, threads).mapToObj(n -> "PRJ-2092-%03d".formatted(n)).toList());
        } finally {
            pool.shutdownNow();
        }
    }

    // The spy sits behind the transactional proxy. Stubbing through the proxy would run the
    // MANDATORY check outside a transaction, so stub the unwrapped spy instead.
    private void failActivityLog() {
        ActivityService spy = AopTestUtils.getUltimateTargetObject(activityService);
        doThrow(new IllegalStateException("activity log unavailable")).when(spy).record(any(), any(), any());
    }

    private Map<String, Long> rowCounts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (String table : TABLES) {
            counts.put(table, jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class));
        }
        return counts;
    }

    private static ClientCreateRequest newClient(String name) {
        return new ClientCreateRequest(MARK + name, ClientKind.INDIVIDUAL, "071 000 0000", null, null, null, null);
    }

    private static DelayTermsDraft delayTerms() {
        return new DelayTermsDraft(LocalDate.of(2091, 12, 1), DelayRule.PER_DAY, new BigDecimal("15000"),
                null, null, 7, null, null);
    }

    private static ProjectCreateRequest construction(ClientCreateRequest client, DelayTermsDraft delay, String start) {
        LocalDate startDate = LocalDate.parse(start);
        return new ProjectCreateRequest(MARK + "Construction", ProjectType.CONSTRUCTION, null, client, null, null,
                new BigDecimal("1000000"), startDate, startDate.plusMonths(6), null, null,
                List.of(new InstallmentDraft("Advance", startDate, new BigDecimal("400000")),
                        new InstallmentDraft("Handover", startDate.plusMonths(6), new BigDecimal("600000"))),
                delay);
    }

    private static ProjectCreateRequest design(long clientId, String start) {
        LocalDate startDate = LocalDate.parse(start);
        return new ProjectCreateRequest(MARK + "Design", ProjectType.DESIGN, clientId, null, null, null,
                new BigDecimal("100000"), startDate, startDate.plusMonths(2), null, null,
                List.of(new InstallmentDraft("Fee", startDate, new BigDecimal("100000"))), null);
    }
}
