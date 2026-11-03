package com.sondurukedella.integration.api;

import static com.sondurukedella.support.TestUsers.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// Page permissions per user: the role is only a name, the Super Admin ticks each user's pages.
// An allowed write with an empty body comes back 400 (it got past security to validation),
// a refused one 403. Rolls back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserPermissionsApiIntegrationTest {

    private static final String USERS = "/api/v1/users";

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
        superAdmin = TestUsers.create(users, encoder, "perm-root", UserRole.SUPER_ADMIN);
        admin = TestUsers.create(users, encoder, "perm-staff", UserRole.ADMIN);
        superSession = login(mockMvc, superAdmin.getUsername());
        adminSession = login(mockMvc, admin.getUsername());
    }

    @Test
    void aSupervisorOpensLabourOnlyAndSeesTheRoleNameOnTheProfile() throws Exception {
        MockHttpSession session = signIn(create("CUSTOM", "  Site   Supervisor ", "[\"LABOUR\"]").andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleName").value("Site Supervisor"))
                .andExpect(jsonPath("$.pages", containsInAnyOrder("LABOUR"))));

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOM"))
                .andExpect(jsonPath("$.roleName").value("Site Supervisor"))
                .andExpect(jsonPath("$.pages", containsInAnyOrder("LABOUR")))
                .andExpect(jsonPath("$.readAreas", hasItem("PAYROLL")))
                .andExpect(jsonPath("$.readAreas", not(hasItem("EXPENSES"))));

        // reads its own data, plus projects for the pickers and the shared settings
        mockMvc.perform(get("/api/v1/employees").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/payroll/advances").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/settings").session(session)).andExpect(status().isOk());
        // writes reach validation on its own pages only
        write(post("/api/v1/overtime"), session).andExpect(status().isBadRequest());
        write(post("/api/v1/payroll/advances"), session).andExpect(status().isBadRequest());
        write(post("/api/v1/projects"), session).andExpect(status().isForbidden());
        write(put("/api/v1/settings/company"), session).andExpect(status().isForbidden());
        // other pages' data stays closed, money on projects included
        mockMvc.perform(get("/api/v1/expenses").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/1/receipts").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/1/schedule").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/1/commissions").session(session)).andExpect(status().isForbidden());
        // an encoded path is checked the way Spring MVC routes it
        mockMvc.perform(get(URI.create("/api/v1/projects/1/%72eceipts")).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get(USERS).session(session)).andExpect(status().isForbidden());
    }

    @Test
    void anAuditorWithReportsReadsEverythingAndChangesNothing() throws Exception {
        MockHttpSession session = signIn(create("CUSTOM", "Auditor", "[\"REPORTS\"]").andExpect(status().isCreated()));
        for (String path : new String[] {"/api/v1/expenses", "/api/v1/projects/1/receipts", "/api/v1/payroll/payments",
                "/api/v1/supplier-invoices", "/api/v1/commissions", "/api/v1/activity", "/api/v1/reports/dashboard"}) {
            int code = mockMvc.perform(get(path).session(session)).andReturn().getResponse().getStatus();
            assertThat(code).as(path).isNotIn(401, 403);
        }
        write(post("/api/v1/expenses"), session).andExpect(status().isForbidden());
        write(post("/api/v1/clients"), session).andExpect(status().isForbidden());
    }

    @Test
    void twoUsersWithTheSameRoleNameHaveTheirOwnPages() throws Exception {
        String one = id(create("CUSTOM", "Supervisor", "[\"LABOUR\"]").andExpect(status().isCreated()));
        MockHttpSession first = login(mockMvc, username(one));
        MockHttpSession second = signIn(create("CUSTOM", "supervisor", "[\"LABOUR\"]").andExpect(status().isCreated()));

        // only the first one gets Expenses; the change applies on their next request, no sign-out
        access(Long.parseLong(one), "{\"role\":\"CUSTOM\",\"roleName\":\"Supervisor\",\"pages\":[\"LABOUR\",\"EXPENSES\"],\"version\":%d}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.pages", hasSize(2)));
        mockMvc.perform(get("/api/v1/expenses").session(first)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/expenses").session(second)).andExpect(status().isForbidden());
        assertThat(events(Long.parseLong(one))).contains("pages EXPENSES, LABOUR");
    }

    @Test
    void anAdminsPagesCanBeNarrowedAndTheyStayAnAdmin() throws Exception {
        mockMvc.perform(get(USERS + "/" + admin.getId()).session(superSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pages", hasSize(11)));
        access(admin.getId(), "{\"role\":\"ADMIN\",\"pages\":[\"INVENTORY\"],\"version\":%d}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.roleName").value("Admin"));

        // same session: pages changed, the role didn't
        mockMvc.perform(get("/api/v1/inventory/materials").session(adminSession)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/expenses").session(adminSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/auth/me").session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roleName").value("Admin"))
                .andExpect(jsonPath("$.pages", containsInAnyOrder("INVENTORY")));
    }

    @Test
    void aNewAdminGetsEveryPageAndASuperAdminTakesNone() throws Exception {
        create("ADMIN", null, null).andExpect(status().isCreated()).andExpect(jsonPath("$.pages", hasSize(11)));
        create("SUPER_ADMIN", null, "[\"REPORTS\"]").andExpect(status().isBadRequest());
        create("SUPER_ADMIN", "Boss", null).andExpect(status().isBadRequest());
        create("SUPER_ADMIN", null, null).andExpect(status().isCreated()).andExpect(jsonPath("$.roleName").value("Super Admin"));
    }

    @Test
    void anOtherRoleNeedsANameThatIsNotBuiltInAndAtLeastOnePage() throws Exception {
        create("CUSTOM", null, "[\"REPORTS\"]").andExpect(status().isBadRequest());
        create("CUSTOM", "  ", "[\"REPORTS\"]").andExpect(status().isBadRequest());
        create("CUSTOM", "Admin", "[\"REPORTS\"]").andExpect(status().isBadRequest());
        create("CUSTOM", "SUPER ADMIN", "[\"REPORTS\"]").andExpect(status().isBadRequest());
        create("CUSTOM", "Viewer", "[]").andExpect(status().isBadRequest());
        create("CUSTOM", "Viewer", null).andExpect(status().isBadRequest());
        create("CUSTOM", "Viewer", "[\"USERS\"]").andExpect(status().isBadRequest());
        create("ADMIN", "Viewer", null).andExpect(status().isBadRequest());
        create("ADMIN", null, "[]").andExpect(status().isBadRequest());
    }

    @Test
    void aNewRoleNameEndsTheSessionAndNobodyEditsTheirOwnAccess() throws Exception {
        access(admin.getId(), "{\"role\":\"CUSTOM\",\"roleName\":\"Storekeeper\",\"pages\":[\"INVENTORY\"],\"version\":%d}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.roleName").value("Storekeeper"));
        mockMvc.perform(get("/api/v1/inventory/materials").session(adminSession)).andExpect(status().isUnauthorized());
        assertThat(events(admin.getId())).anyMatch(d -> d.startsWith("Admin -> Storekeeper"));

        access(superAdmin.getId(), "{\"role\":\"ADMIN\",\"version\":%d}").andExpect(status().isUnprocessableContent());
    }

    @Test
    void onlyASuperAdminChangesAccess() throws Exception {
        mockMvc.perform(put(USERS + "/" + superAdmin.getId() + "/role").session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\",\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions create(String role, String roleName, String pagesJson) throws Exception {
        String username = "perm-" + System.nanoTime();
        String json = "{\"username\":\"%s\",\"displayName\":\"Perm\",\"email\":\"%s@example.com\",\"password\":\"%s\",\"role\":\"%s\"%s%s}"
                .formatted(username, username, TestUsers.PASSWORD, role,
                        roleName == null ? "" : ",\"roleName\":\"" + roleName + "\"",
                        pagesJson == null ? "" : ",\"pages\":" + pagesJson);
        return mockMvc.perform(post(USERS).session(superSession).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private MockHttpSession signIn(ResultActions created) throws Exception {
        return login(mockMvc, username(id(created)));
    }

    private static String id(ResultActions created) throws Exception {
        return String.valueOf(((Number) JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id")).longValue());
    }

    private String username(String id) {
        return jdbc.queryForObject("SELECT username FROM app_users WHERE id = ?", String.class, Long.parseLong(id));
    }

    private ResultActions access(long userId, String jsonWithVersion) throws Exception {
        long version = jdbc.queryForObject("SELECT version FROM app_users WHERE id = ?", Long.class, userId);
        return mockMvc.perform(put(USERS + "/" + userId + "/role").session(superSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(jsonWithVersion.formatted(version)));
    }

    private ResultActions write(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        return mockMvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private List<String> events(long targetId) {
        return jdbc.queryForList("SELECT details FROM security_events WHERE target_user_id = ? AND event_type = 'ROLE_CHANGED'",
                String.class, targetId);
    }
}
