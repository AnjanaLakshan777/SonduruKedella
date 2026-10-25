package com.sondurukedella.integration.api;

import static com.sondurukedella.support.TestUsers.PASSWORD;
import static com.sondurukedella.support.TestUsers.from;
import static com.sondurukedella.support.TestUsers.login;
import static com.sondurukedella.support.TestUsers.loginJson;
import static com.sondurukedella.support.TestUsers.randomAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sondurukedella.security.user.AppUser;
import com.sondurukedella.security.user.AppUserRepository;
import com.sondurukedella.security.user.UserRole;
import com.sondurukedella.support.TestUsers;
import java.util.List;
import java.util.Map;
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

// /api/v1/users with real accounts and real sessions: Super Admin only, no privilege escalation,
// and access changes take effect on existing sessions. Each test rolls back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAdministrationApiIntegrationTest {

    private static final String URL = "/api/v1/users";

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
        superAdmin = TestUsers.create(users, encoder, "root", UserRole.SUPER_ADMIN);
        admin = TestUsers.create(users, encoder, "staff", UserRole.ADMIN);
        superSession = login(mockMvc, superAdmin.getUsername());
        adminSession = login(mockMvc, admin.getUsername());
    }

    @Test
    void aSuperAdminCreatesAnAdminWhoCanSignIn() throws Exception {
        String username = "nimal-" + System.nanoTime();
        String json = create(superSession, """
                {"username":"  %s ","displayName":" Nimal Perera ","password":"nimal-password-12"}""".formatted(username.toUpperCase()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(URL + "/")))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.displayName").value("Nimal Perera"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("nimal-password-12").doesNotContain("bcrypt");
        long id = id(json);

        login(mockMvc, username, "nimal-password-12");
        assertThat(events(id)).extracting(e -> e.get("event_type")).contains("USER_CREATED");
        assertThat(events(id).get(0).get("actor_user_id")).isEqualTo(superAdmin.getId());
    }

    @Test
    void aSuperAdminMayCreateAnotherSuperAdmin() throws Exception {
        create(superSession, """
                {"username":"second-root-%d","displayName":"Second","password":"second-password-1","role":"SUPER_ADMIN"}""".formatted(System.nanoTime()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"));
    }

    @Test
    void invalidAccountsAreRejected() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM app_users", Long.class);
        create(superSession, "{\"username\":\"ok-name\",\"displayName\":\"X\",\"password\":\"short\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("password"));
        create(superSession, "{\"username\":\"ok-name\",\"displayName\":\"X\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("password"));
        create(superSession, "{\"username\":\"bad name!\",\"displayName\":\"X\",\"password\":\"long-enough-pw-1\"}")
                .andExpect(status().isBadRequest());
        create(superSession, "{\"username\":\"ab\",\"displayName\":\"X\",\"password\":\"long-enough-pw-1\"}")
                .andExpect(status().isBadRequest());
        // only the two approved roles exist
        create(superSession, "{\"username\":\"cashier-1\",\"displayName\":\"X\",\"password\":\"long-enough-pw-1\",\"role\":\"CASHIER\"}")
                .andExpect(status().isBadRequest());
        create(superSession, "{\"username\":\"" + admin.getUsername().toUpperCase() + "\",\"displayName\":\"X\",\"password\":\"long-enough-pw-1\"}")
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Long.class)).isEqualTo(before);
    }

    @Test
    void anAdminCannotUseAnyUserAdministrationEndpoint() throws Exception {
        long target = admin.getId();
        List<MockHttpServletRequestBuilder> requests = List.of(
                get(URL),
                get(URL + "/" + superAdmin.getId()),
                post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sneaky-1\",\"displayName\":\"S\",\"password\":\"sneaky-password-1\",\"role\":\"SUPER_ADMIN\"}"),
                put(URL + "/" + superAdmin.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Hacked\",\"version\":0}"),
                put(URL + "/" + target + "/role").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SUPER_ADMIN\",\"version\":0}"),
                put(URL + "/" + superAdmin.getId() + "/role").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\",\"version\":0}"),
                put(URL + "/" + superAdmin.getId() + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"version\":0}"),
                put(URL + "/" + superAdmin.getId() + "/password").contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"taken-over-pw-12\"}"));
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request.session(adminSession).with(csrf())).andExpect(status().isForbidden());
        }
        assertThat(users.findById(target).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
        AppUser root = users.findById(superAdmin.getId()).orElseThrow();
        assertThat(root.isActiveSuperAdmin()).isTrue();
        assertThat(root.getDisplayName()).isNotEqualTo("Hacked");
        assertThat(encoder.matches(PASSWORD, root.getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users WHERE username = 'sneaky-1'", Long.class)).isZero();
    }

    @Test
    void anonymousCallersCannotReachUserAdministration() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"anon-1\",\"displayName\":\"A\",\"password\":\"anon-password-12\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersAreListedAndFiltered() throws Exception {
        mockMvc.perform(get(URL).session(superSession).param("role", "ADMIN").param("q", admin.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(admin.getId()))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mockMvc.perform(get(URL).session(superSession).param("role", "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].role", everyItem(is("SUPER_ADMIN"))))
                .andExpect(jsonPath("$.content[*].id", hasItem(superAdmin.getId().intValue())));
        mockMvc.perform(get(URL).session(superSession).param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).session(superSession).param("sort", "passwordHash")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL + "/" + admin.getId()).session(superSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(admin.getUsername()));
        mockMvc.perform(get(URL + "/999999999").session(superSession)).andExpect(status().isNotFound());
    }

    @Test
    void theDisplayNameCanBeEditedWithTheCurrentVersion() throws Exception {
        long version = version(admin.getId());
        mockMvc.perform(put(URL + "/" + admin.getId()).session(superSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\" Kamala \",\"version\":%d}".formatted(version)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Kamala"));
        mockMvc.perform(put(URL + "/" + admin.getId()).session(superSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Stale\",\"version\":%d}".formatted(version)))
                .andExpect(status().isConflict());
    }

    @Test
    void promotingAnAdminEndsTheirOldSessionAndTheNextSignInHasTheNewRole() throws Exception {
        changeRole(superSession, admin.getId(), "SUPER_ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SUPER_ADMIN"));

        mockMvc.perform(get("/api/v1/projects").session(adminSession)).andExpect(status().isUnauthorized());
        MockHttpSession fresh = login(mockMvc, admin.getUsername());
        mockMvc.perform(get(URL).session(fresh)).andExpect(status().isOk());
        assertThat(events(admin.getId())).extracting(e -> e.get("details")).contains("ADMIN -> SUPER_ADMIN");
    }

    @Test
    void demotingASuperAdminTakesUserAdministrationAwayAtOnce() throws Exception {
        AppUser other = TestUsers.create(users, encoder, "root2", UserRole.SUPER_ADMIN);
        MockHttpSession otherSession = login(mockMvc, other.getUsername());
        changeRole(superSession, other.getId(), "ADMIN").andExpect(status().isOk());

        mockMvc.perform(get(URL).session(otherSession)).andExpect(status().isUnauthorized());
        MockHttpSession fresh = login(mockMvc, other.getUsername());
        mockMvc.perform(get(URL).session(fresh)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects").session(fresh)).andExpect(status().isOk());
    }

    @Test
    void nobodyChangesTheirOwnRoleOrStatus() throws Exception {
        changeRole(superSession, superAdmin.getId(), "ADMIN").andExpect(status().isUnprocessableContent());
        setStatus(superSession, superAdmin.getId(), false).andExpect(status().isUnprocessableContent());
        mockMvc.perform(put(URL + "/" + superAdmin.getId() + "/password").session(superSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"my-own-new-pw-12\"}"))
                .andExpect(status().isUnprocessableContent());
        assertThat(users.findById(superAdmin.getId()).orElseThrow().isActiveSuperAdmin()).isTrue();
    }

    @Test
    void theLastActiveSuperAdminStays() throws Exception {
        // make this the only active Super Admin in the (rolled back) database
        jdbc.update("UPDATE app_users SET enabled = FALSE WHERE role = 'SUPER_ADMIN' AND id <> ?", superAdmin.getId());
        changeRole(superSession, superAdmin.getId(), "ADMIN").andExpect(status().isUnprocessableContent());
        setStatus(superSession, superAdmin.getId(), false).andExpect(status().isUnprocessableContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users WHERE role = 'SUPER_ADMIN' AND enabled", Long.class)).isOne();
    }

    @Test
    void aDeactivatedUserLosesAccessImmediatelyAndCannotSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/projects").session(adminSession)).andExpect(status().isOk());
        setStatus(superSession, admin.getId(), false).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/v1/projects").session(adminSession)).andExpect(status().isUnauthorized());
        assertThat(adminSession.isInvalid()).isTrue();
        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).with(from(randomAddress())).contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(admin.getUsername(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));

        setStatus(superSession, admin.getId(), true).andExpect(status().isOk());
        login(mockMvc, admin.getUsername());
        assertThat(events(admin.getId())).extracting(e -> e.get("event_type")).contains("USER_DEACTIVATED", "USER_ACTIVATED");
    }

    @Test
    void aPasswordResetEndsSessionsAndIsNeverEchoed() throws Exception {
        String body = mockMvc.perform(put(URL + "/" + admin.getId() + "/password").session(superSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"reset-by-root-12\"}"))
                .andExpect(status().isNoContent()).andReturn().getResponse().getContentAsString();
        assertThat(body).isEmpty();

        mockMvc.perform(get("/api/v1/projects").session(adminSession)).andExpect(status().isUnauthorized());
        login(mockMvc, admin.getUsername(), "reset-by-root-12");
        mockMvc.perform(put(URL + "/" + admin.getId() + "/password").session(superSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        assertThat(events(admin.getId()).toString()).contains("PASSWORD_RESET").doesNotContain("reset-by-root-12");
    }

    @Test
    void roleAndStatusChangesNeedTheCurrentVersion() throws Exception {
        mockMvc.perform(put(URL + "/" + admin.getId() + "/role").session(superSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPER_ADMIN\",\"version\":%d}".formatted(version(admin.getId()) + 5)))
                .andExpect(status().isConflict());
        mockMvc.perform(put(URL + "/" + admin.getId() + "/status").session(superSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(URL + "/999999999/status").session(superSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    private ResultActions create(MockHttpSession session, String json) throws Exception {
        return mockMvc.perform(post(URL).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions changeRole(MockHttpSession session, long id, String role) throws Exception {
        return mockMvc.perform(put(URL + "/" + id + "/role").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"%s\",\"version\":%d}".formatted(role, version(id))));
    }

    private ResultActions setStatus(MockHttpSession session, long id, boolean enabled) throws Exception {
        return mockMvc.perform(put(URL + "/" + id + "/status").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":%s,\"version\":%d}".formatted(enabled, version(id))));
    }

    private long version(long id) {
        return jdbc.queryForObject("SELECT version FROM app_users WHERE id = ?", Long.class, id);
    }

    private List<Map<String, Object>> events(long targetId) {
        return jdbc.queryForList("SELECT event_type, actor_user_id, details FROM security_events WHERE target_user_id = ? ORDER BY id", targetId);
    }

    private static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
