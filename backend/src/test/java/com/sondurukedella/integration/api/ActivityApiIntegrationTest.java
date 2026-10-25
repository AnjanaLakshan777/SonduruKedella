package com.sondurukedella.integration.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// The activity feed reads what the other modules wrote. Rolls back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class ActivityApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aProjectsFeedIsNewestFirstAndFiltered() throws Exception {
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Feed Client', '0710000000') RETURNING id", Long.class);
        long project = ApiFixtures.project(mockMvc, client, "CONSTRUCTION", "Feed House"); // writes a STATUS entry
        ApiFixtures.setProjectStatus(mockMvc, project, "IN_PROGRESS", 0);                 // and another
        jdbc.update("INSERT INTO activity_log (project_id, activity_date, kind, message) VALUES (?, DATE '2026-01-05', 'NOTE', 'Old note')", project);

        mockMvc.perform(get("/api/v1/activity").param("projectId", String.valueOf(project)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].kind").value("STATUS"))
                .andExpect(jsonPath("$.content[0].message").value(org.hamcrest.Matchers.containsString("In Progress")))
                .andExpect(jsonPath("$.content[2].message").value("Old note"));
        mockMvc.perform(get("/api/v1/activity").param("projectId", String.valueOf(project)).param("kind", "NOTE"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/activity").param("projectId", String.valueOf(project)).param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(jsonPath("$.content[0].message").value("Old note"));
        mockMvc.perform(get("/api/v1/activity").param("projectId", "999999999")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidRequestsAreRejectedAndTheFeedIsReadOnly() throws Exception {
        mockMvc.perform(get("/api/v1/activity").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/activity").param("sort", "message")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/activity").param("from", "2026-02-01").param("to", "2026-01-01")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/activity").param("kind", "GOSSIP")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/activity").with(csrf())).andExpect(status().isMethodNotAllowed());
    }
}
