package com.sondurukedella.integration.api;

import static com.sondurukedella.support.TestUsers.from;
import static com.sondurukedella.support.TestUsers.login;
import static com.sondurukedella.support.TestUsers.randomAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sondurukedella.security.user.AppUser;
import com.sondurukedella.security.user.AppUserRepository;
import com.sondurukedella.security.user.UserRole;
import com.sondurukedella.support.TestUsers;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Audit history: every create/change through the app leaves a row with who, when, the request and
// old -> new per field; only a Super Admin can read it, and nobody can change it. Rolls back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditApiIntegrationTest {

    private static final String CHANGES = "/api/v1/audit/changes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    private AppUser superAdmin;
    private AppUser admin;
    private MockHttpSession superSession;
    private MockHttpSession adminSession;

    @BeforeEach
    void setUp() throws Exception {
        superAdmin = TestUsers.create(users, encoder, "audit-root", UserRole.SUPER_ADMIN);
        admin = TestUsers.create(users, encoder, "audit-staff", UserRole.ADMIN);
        superSession = login(mockMvc, superAdmin.getUsername());
        adminSession = login(mockMvc, admin.getUsername());
    }

    @Test
    void creatingAndEditingARecordLeavesWhoWhenAndOldToNew() throws Exception {
        String name = "Audit Client " + System.nanoTime();
        String json = mockMvc.perform(post("/api/v1/clients").session(adminSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"kind\":\"INDIVIDUAL\",\"phone\":\"0710000000\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = String.valueOf(((Number) JsonPath.read(json, "$.id")).longValue());
        mockMvc.perform(put("/api/v1/clients/" + id).session(adminSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s Renamed\",\"kind\":\"INDIVIDUAL\",\"phone\":\"0710000000\",\"version\":0}".formatted(name)))
                .andExpect(status().isOk());

        mockMvc.perform(get(CHANGES).param("entityType", "Client").param("entityId", id).session(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                // newest first: the edit
                .andExpect(jsonPath("$.content[0].changeType").value("UPDATED"))
                .andExpect(jsonPath("$.content[0].userId").value(admin.getId()))
                .andExpect(jsonPath("$.content[0].username").value(admin.getUsername()))
                .andExpect(jsonPath("$.content[0].httpMethod").value("PUT"))
                .andExpect(jsonPath("$.content[0].path").value("/api/v1/clients/" + id))
                .andExpect(jsonPath("$.content[0].changes.name[0]").value(name))
                .andExpect(jsonPath("$.content[0].changes.name[1]").value(name + " Renamed"))
                // bookkeeping columns are left out
                .andExpect(jsonPath("$.content[0].changes.version").doesNotExist())
                .andExpect(jsonPath("$.content[0].changes.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.content[1].changeType").value("CREATED"))
                .andExpect(jsonPath("$.content[1].changes.name[1]").value(name));

        mockMvc.perform(get("/api/v1/audit/entity-types").session(superSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasItem("Client")));
        mockMvc.perform(get(CHANGES).param("userId", String.valueOf(admin.getId())).session(superSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].entityId", hasItem(id)));
    }

    @Test
    void aPasswordChangeIsRecordedButNeverTheHash() throws Exception {
        mockMvc.perform(put("/api/v1/auth/me/password").session(adminSession).with(csrf()).with(from(randomAddress()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"brand-new-pass-12\"}".formatted(TestUsers.PASSWORD)))
                .andExpect(status().isNoContent());

        String json = mockMvc.perform(get(CHANGES).param("entityType", "AppUser").param("entityId", String.valueOf(admin.getId()))
                        .session(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].changes.passwordHash[1]").value("(changed)"))
                .andExpect(jsonPath("$.content[0].changes.authVersion").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("bcrypt").doesNotContain("brand-new-pass-12");
        List<String> stored = jdbc.queryForList("SELECT changes::text FROM audit_events WHERE entity_type = 'AppUser'", String.class);
        assertThat(stored).noneMatch(s -> s.contains("bcrypt"));
    }

    @Test
    void signInsAndAccountChangesAreOnTheSecurityList() throws Exception {
        mockMvc.perform(get("/api/v1/audit/security").param("userId", String.valueOf(admin.getId())).session(superSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].eventType", hasItem("LOGIN_SUCCEEDED")));
    }

    @Test
    void onlyASuperAdminReadsTheAuditHistory() throws Exception {
        mockMvc.perform(get(CHANGES).session(adminSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit/security").session(adminSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit/entity-types").session(adminSession)).andExpect(status().isForbidden());
        mockMvc.perform(get(CHANGES)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(CHANGES).param("size", "500").session(superSession)).andExpect(status().isBadRequest());
        mockMvc.perform(get(CHANGES).param("from", "2026-11-10").param("to", "2026-11-01").session(superSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditRowsCannotBeChangedOrDeleted() {
        jdbc.update("INSERT INTO audit_events (entity_type, entity_id, change_type) VALUES ('Client', '1', 'CREATED')");
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET entity_id = '2' WHERE entity_type = 'Client' AND entity_id = '1'"))
                .isInstanceOf(DataAccessException.class);
    }
}
