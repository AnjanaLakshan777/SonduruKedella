package com.sondurukedella.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Revising the unbilled schedule, variations and write-off reversal. Rolls back.
// Project: contract 1,000,000 = "Foundation" 600,000 + "Roof" 400,000.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class ScheduleAndWriteOffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long project;
    private long foundation;
    private long roof;

    @BeforeEach
    void setUp() throws Exception {
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Sched Client', '0710000000') RETURNING id", Long.class);
        String json = mockMvc.perform(post("/api/v1/projects").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Sched House","type":"CONSTRUCTION","clientId":%d,"contractValue":1000000,"startDate":"2026-01-01",
                         "expectedCompletion":"2026-12-31","schedule":[{"description":"Foundation","dueDate":"2026-03-01","amount":600000},
                         {"description":"Roof","dueDate":"2026-09-01","amount":400000}]}""".formatted(client)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        project = ((Number) JsonPath.read(json, "$.id")).longValue();
        foundation = jdbc.queryForObject("SELECT id FROM installments WHERE project_id = ? AND description = 'Foundation'", Long.class, project);
        roof = jdbc.queryForObject("SELECT id FROM installments WHERE project_id = ? AND description = 'Roof'", Long.class, project);
    }

    @Test
    void theUnbilledPartOfTheScheduleCanBeRevisedButBilledRowsAreLocked() throws Exception {
        bill(foundation);
        // split the roof into two, keep the billed foundation as it is
        revise(row(foundation, version(foundation), "Foundation", "2026-03-01", "600000"), row(null, null, "Roof", "2026-09-01", "250000"),
                row(null, null, "Handover", "2026-12-01", "150000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM installments WHERE id = ?", Long.class, roof)).isZero(); // the old unbilled roof went
        assertThat(jdbc.queryForObject("SELECT sum(amount)::text FROM installments WHERE project_id = ?", String.class, project)).isEqualTo("1000000.00");

        // must still add up to the contract value
        long newRoof = jdbc.queryForObject("SELECT id FROM installments WHERE project_id = ? AND description = 'Roof'", Long.class, project);
        long handover = jdbc.queryForObject("SELECT id FROM installments WHERE project_id = ? AND description = 'Handover'", Long.class, project);
        revise(row(foundation, version(foundation), "Foundation", "2026-03-01", "600000"), row(newRoof, version(newRoof), "Roof", "2026-09-01", "300000"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("must match")));
        // a billed row can't change or go
        revise(row(foundation, version(foundation), "Foundation", "2026-04-01", "600000"), row(newRoof, version(newRoof), "Roof", "2026-09-01", "250000"),
                row(handover, version(handover), "Handover", "2026-12-01", "150000"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail").value(containsString("billed")));
        revise(row(newRoof, version(newRoof), "Roof", "2026-09-01", "850000"), row(handover, version(handover), "Handover", "2026-12-01", "150000"))
                .andExpect(status().isUnprocessableContent());
        // stale version
        revise(row(foundation, version(foundation), "Foundation", "2026-03-01", "600000"), row(newRoof, version(newRoof) + 3, "Roof", "2026-09-01", "250000"),
                row(handover, version(handover), "Handover", "2026-12-01", "150000"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_log WHERE project_id = ? AND message = 'Payment schedule revised.'",
                Long.class, project)).isOne();
    }

    @Test
    void aVariationIsBilledAtOnceAndLeavesTheContractValueAlone() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + project + "/variations").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Extra boundary wall\",\"amount\":75000,\"dueDate\":\"2026-11-30\",\"billedOn\":\"2026-10-20\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("VARIATION"))
                .andExpect(jsonPath("$.billedOn").value("2026-10-20"));
        mockMvc.perform(get("/api/v1/projects/" + project + "/receivables"))
                .andExpect(jsonPath("$.totals.revenueRecognised").value(75000.00))
                .andExpect(jsonPath("$.totals.outstanding").value(75000.00));
        assertThat(jdbc.queryForObject("SELECT contract_value::text FROM projects WHERE id = ?", String.class, project)).isEqualTo("1000000.00");
        mockMvc.perform(post("/api/v1/projects/" + project + "/variations").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\" \",\"amount\":0,\"dueDate\":\"2026-11-30\"}")).andExpect(status().isBadRequest());
        // a variation is not part of the scheduled plan: revising leaves it alone
        revise(row(foundation, version(foundation), "Foundation", "2026-03-01", "500000"), row(roof, version(roof), "Roof", "2026-09-01", "500000"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM installments WHERE project_id = ? AND kind = 'VARIATION'", Long.class, project)).isOne();
    }

    @Test
    void aWriteOffCanBeReversedOnceAndStopsCounting() throws Exception {
        bill(foundation);
        String json = mockMvc.perform(post("/api/v1/projects/" + project + "/installments/" + foundation + "/write-offs").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-10-22\",\"amount\":100000,\"reason\":\"Client disputes the work\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long writeOff = ((Number) JsonPath.read(json, "$.id")).longValue();
        String summaryBefore = mockMvc.perform(get("/api/v1/reports/financial-summary?from=2026-10-01&to=2026-10-31")).andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/v1/projects/" + project + "/receivables")).andExpect(jsonPath("$.totals.outstanding").value(500000.00));

        reverse(writeOff, "Dispute settled, client will pay", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true)).andExpect(jsonPath("$.voidReason").value("Dispute settled, client will pay"));
        reverse(writeOff, "again", 1).andExpect(status().isConflict());
        reverse(writeOff, " ", 1).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/projects/" + project + "/receivables"))
                .andExpect(jsonPath("$.totals.outstanding").value(600000.00))
                .andExpect(jsonPath("$.totals.writtenOff").value(0));
        String summaryAfter = mockMvc.perform(get("/api/v1/reports/financial-summary?from=2026-10-01&to=2026-10-31")).andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(summaryBefore, "$.revenue.writtenOff")).doubleValue()
                - ((Number) JsonPath.read(summaryAfter, "$.revenue.writtenOff")).doubleValue()).isEqualTo(100000.0);
        // the row stays
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receivable_write_offs WHERE id = ?", Long.class, writeOff)).isOne();
        reverse(999_999_999L, "x", 0).andExpect(status().isNotFound());
    }

    private void bill(long installmentId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + project + "/installments/" + installmentId + "/bill").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"billedOn\":\"2026-10-20\",\"version\":" + version(installmentId) + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions revise(String... rows) throws Exception {
        return mockMvc.perform(put("/api/v1/projects/" + project + "/schedule").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"installments\":[" + String.join(",", rows) + "]}"));
    }

    private ResultActions reverse(long writeOffId, String reason, long version) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/" + project + "/write-offs/" + writeOffId + "/reverse").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"%s\",\"version\":%d}".formatted(reason, version)));
    }

    private static String row(Long id, Long version, String description, String due, String amount) {
        return "{" + (id == null ? "" : "\"id\":" + id + ",\"version\":" + version + ",")
                + "\"installment\":{\"description\":\"" + description + "\",\"dueDate\":\"" + due + "\",\"amount\":" + amount + "}}";
    }

    private long version(long installmentId) {
        return jdbc.queryForObject("SELECT version FROM installments WHERE id = ?", Long.class, installmentId);
    }
}
