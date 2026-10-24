package com.sondurukedella.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sondurukedella.config.ClockConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// End-to-end against a real database with a fixed clock. @Transactional rolls each test back.
// Rollback on failure is proven separately in ProjectTransactionIntegrationTest, which commits for real.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser
class ProjectApiIntegrationTest {

    private static final String PROJECTS = "/api/v1/projects";
    // 19:00 UTC on the 24th is already the 25th in Colombo
    private static final LocalDate COLOMBO_TODAY = LocalDate.of(2026, 10, 25);

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-24T19:00:00Z"), ClockConfig.BUSINESS_ZONE);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createsProjectInlineClientScheduleAndDelayTermsTogether() throws Exception {
        String body = """
                {"name":"Nuwarawewa Lakeside Villa","type":"CONSTRUCTION",
                 "newClient":{"name":"Nirosha Herath","kind":"INDIVIDUAL","phone":"076 904 1187","city":"Medawachchiya"},
                 "location":"Nuwarawewa, Anuradhapura","contractValue":12500000,
                 "startDate":"2031-02-01","expectedCompletion":"2031-12-20","status":"IN_PROGRESS",
                 "schedule":[{"description":"Mobilisation advance","dueDate":"2031-02-01","amount":2500000},
                             {"description":"Roof slab","dueDate":"2031-07-01","amount":6250000.50},
                             {"description":"Handover","dueDate":"2031-12-20","amount":3749999.50}],
                 "delayTerms":{"contractDeadline":"2031-12-20","rule":"PER_DAY","ratePerDay":15000,
                               "fixedAmount":999,"graceDays":7,"maxDeduction":625000,"notes":"Clause 14.2"}}""";

        String json = mockMvc.perform(post(PROJECTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PRJ-2031-001"))
                .andExpect(jsonPath("$.clientName").value("Nirosha Herath"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.contractValue").value(12500000.00))
                .andExpect(jsonPath("$.actualCompletion").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        long clientId = ((Number) JsonPath.read(json, "$.clientId")).longValue();

        assertThat(jdbc.queryForObject("SELECT name FROM clients WHERE id = ?", String.class, clientId))
                .isEqualTo("Nirosha Herath");
        List<Map<String, Object>> installments = jdbc.queryForList(
                "SELECT description, amount, kind, billed_on FROM installments WHERE project_id = ? ORDER BY due_date", id);
        assertThat(installments).hasSize(3);
        assertThat(installments).allSatisfy(row -> {
            assertThat(row.get("kind")).isEqualTo("SCHEDULED");
            assertThat(row.get("billed_on")).isNull(); // billing is what recognises revenue, not creation
        });
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM installments WHERE project_id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("12500000.00");

        Map<String, Object> terms = jdbc.queryForMap("SELECT * FROM delay_deduction_terms WHERE project_id = ?", id);
        assertThat(terms.get("rule")).isEqualTo("PER_DAY");
        assertThat((BigDecimal) terms.get("rate_per_day")).isEqualByComparingTo("15000");
        assertThat(terms.get("fixed_amount")).isNull(); // the stray value for another rule was dropped
        assertThat(terms.get("grace_days")).isEqualTo(7);
        assertThat(terms.get("applied_amount")).isNull();
        // the contract value is untouched by delay terms
        assertThat(jdbc.queryForObject("SELECT contract_value FROM projects WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("12500000.00");

        assertThat(activity(id)).containsExactly(Map.of("kind", "STATUS", "message", "Project created in In Progress.",
                "activity_date", java.sql.Date.valueOf(COLOMBO_TODAY)));
    }

    @Test
    void codesAreNumberedPerStartYear() throws Exception {
        long client = client("Code Client");
        assertThat(code(create(design(client, "2032-03-01", "2032-06-01")))).isEqualTo("PRJ-2032-001");
        assertThat(code(create(design(client, "2032-09-01", "2032-12-01")))).isEqualTo("PRJ-2032-002");
        assertThat(code(create(design(client, "2033-01-15", "2033-04-01")))).isEqualTo("PRJ-2033-001");
        assertThat(code(create(design(client, "2032-11-01", "2033-02-01")))).isEqualTo("PRJ-2032-003");
    }

    @Test
    void aConsultationCanStartWithNoFeeAndNoSchedule() throws Exception {
        long client = client("Consult Client");
        long id = id(create("""
                {"name":"Site report","type":"CONSULTATION","clientId":%d,"contractValue":0,
                 "startDate":"2031-04-01","expectedCompletion":"2031-04-30"}""".formatted(client)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM installments WHERE project_id = ?", Long.class, id)).isZero();
    }

    @Test
    void theDetailAndListIncludeTheClientNameAndSearchCoversIt() throws Exception {
        long client = client("Thilina Wickramasinghe");
        long id = id(create(design(client, "2034-05-01", "2034-08-01")));

        mockMvc.perform(get(PROJECTS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Thilina Wickramasinghe"))
                .andExpect(jsonPath("$.startDate").value("2034-05-01"))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(get(PROJECTS).param("q", "wickrama"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem((int) id)))
                .andExpect(jsonPath("$.content[0].clientName").value("Thilina Wickramasinghe"))
                .andExpect(jsonPath("$.size").value(10));
        mockMvc.perform(get(PROJECTS).param("q", "PRJ-2034").param("type", "DESIGN")
                        .param("startFrom", "2034-01-01").param("startTo", "2034-12-31"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(PROJECTS).param("clientId", String.valueOf(client)).param("type", "CONSTRUCTION"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void statusChangesFollowTheCompletionDateRulesAndAreLogged() throws Exception {
        long id = id(create(design(client("Status Client"), "2031-01-10", "2031-03-10")));

        changeStatus(id, "IN_PROGRESS", 0).andExpect(jsonPath("$.actualCompletion").doesNotExist());
        changeStatus(id, "COMPLETED", 1).andExpect(jsonPath("$.actualCompletion").value(COLOMBO_TODAY.toString()));
        // reopening keeps the date, and completing again doesn't overwrite it
        changeStatus(id, "PLANNING", 2).andExpect(jsonPath("$.actualCompletion").value(COLOMBO_TODAY.toString()));
        jdbc.update("UPDATE projects SET actual_completion = DATE '2031-03-01' WHERE id = ?", id);
        // the whole test shares one persistence context, so drop the cached project to see that update
        entityManager.clear();
        changeStatus(id, "COMPLETED", 3).andExpect(jsonPath("$.actualCompletion").value("2031-03-01"));
        changeStatus(id, "CANCELLED", 4).andExpect(jsonPath("$.status").value("CANCELLED"));
        changeStatus(id, "ON_HOLD", 5).andExpect(jsonPath("$.version").value(6));

        assertThat(activity(id)).extracting(row -> row.get("message")).containsExactly(
                "Project created in Planning.",
                "Status changed from Planning to In Progress.",
                "Status changed from In Progress to Completed.",
                "Status changed from Completed to Planning.",
                "Status changed from Planning to Completed.",
                "Status changed from Completed to Cancelled.",
                "Status changed from Cancelled to On Hold.");
    }

    @Test
    void aStaleStatusChangeIsAConflictAndChangesNothing() throws Exception {
        long id = id(create(design(client("Stale Client"), "2031-01-10", "2031-03-10")));
        changeStatus(id, "IN_PROGRESS", 0);

        mockMvc.perform(put(PROJECTS + "/" + id + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"version\":0}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get(PROJECTS + "/" + id))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.actualCompletion").doesNotExist());
        assertThat(activity(id)).hasSize(2);
    }

    @Test
    void invalidCreatesAreRejectedWithAReason() throws Exception {
        long client = client("Invalid Client");
        expect400(design(Long.MAX_VALUE, "2031-01-10", "2031-03-10"), "client does not exist");
        expect400(design(client, "2031-01-10", "2031-03-10").replace("\"amount\":100000", "\"amount\":99999.99"),
                "must match exactly");
        expect400(design(client, "2031-01-10", "2031-03-10").replace("}]}",
                "}],\"delayTerms\":{\"contractDeadline\":\"2031-03-10\",\"rule\":\"PER_DAY\",\"ratePerDay\":100}}"),
                "only apply to construction");
        expect400(design(client, "2031-01-10", "2031-03-10").replace("\"type\":\"DESIGN\"",
                "\"type\":\"DESIGN\",\"status\":\"COMPLETED\""), "Planning, In Progress or On Hold");
        expect400(design(client, "2031-01-10", "2031-01-10"), "after the start date");
        expect400("""
                {"name":"No schedule","type":"DESIGN","clientId":%d,"contractValue":100000,
                 "startDate":"2031-01-10","expectedCompletion":"2031-03-10"}""".formatted(client), "payment schedule");
    }

    @Test
    void invalidListingParametersAreBadRequests() throws Exception {
        mockMvc.perform(get(PROJECTS).param("sort", "received,desc")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PROJECTS).param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PROJECTS).param("startFrom", "2027-01-01").param("startTo", "2026-01-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PROJECTS).param("sort", "contractValue,desc")).andExpect(status().isOk());
    }

    @Test
    void missingProjectsAre404() throws Exception {
        mockMvc.perform(get(PROJECTS + "/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
        mockMvc.perform(put(PROJECTS + "/" + Long.MAX_VALUE + "/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ON_HOLD\",\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get(PROJECTS).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    private static String design(long clientId, String start, String end) {
        return """
                {"name":"House plan & 3D","type":"DESIGN","clientId":%d,"contractValue":100000,
                 "startDate":"%s","expectedCompletion":"%s",
                 "schedule":[{"description":"Full fee","dueDate":"%s","amount":100000}]}"""
                .formatted(clientId, start, end, start);
    }

    private long client(String name) {
        return jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES (?, '0710000000') RETURNING id",
                Long.class, name);
    }

    private String create(String body) throws Exception {
        return mockMvc.perform(post(PROJECTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private void expect400(String body, String reason) throws Exception {
        mockMvc.perform(post(PROJECTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString(reason)));
    }

    private ResultActions changeStatus(long id, String status, long version) throws Exception {
        return mockMvc.perform(put(PROJECTS + "/" + id + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"%s\",\"version\":%d}".formatted(status, version)))
                .andExpect(status().isOk());
    }

    private List<Map<String, Object>> activity(long projectId) {
        return jdbc.queryForList(
                "SELECT kind, message, activity_date FROM activity_log WHERE project_id = ? ORDER BY id", projectId);
    }

    private static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static String code(String json) {
        return JsonPath.read(json, "$.code");
    }
}
