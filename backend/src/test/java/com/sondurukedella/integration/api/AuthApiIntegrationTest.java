package com.sondurukedella.integration.api;

import static com.sondurukedella.support.TestUsers.PASSWORD;
import static com.sondurukedella.support.TestUsers.from;
import static com.sondurukedella.support.TestUsers.login;
import static com.sondurukedella.support.TestUsers.loginJson;
import static com.sondurukedella.support.TestUsers.randomAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sondurukedella.security.user.AppUser;
import com.sondurukedella.security.user.AppUserRepository;
import com.sondurukedella.security.user.UserRole;
import com.sondurukedella.support.TestUsers;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.support.WebTestUtils;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

// Login, logout, sessions and CSRF through the real filter chain with real users, no mocks. Rolls back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/auth/me";
    private static final String GENERIC = "Invalid username, email or password.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CsrfTokenRepository csrfTokenRepository;

    private AppUser admin;

    @BeforeEach
    void setUp() {
        admin = TestUsers.create(users, encoder, "auth", UserRole.ADMIN);
    }

    @Test
    void aValidLoginReturnsTheProfileWithoutAnyCredential() throws Exception {
        String body = attempt(admin.getUsername(), PASSWORD, randomAddress())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId()))
                .andExpect(jsonPath("$.username").value(admin.getUsername()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD).doesNotContain("bcrypt").doesNotContain("$2");
    }

    @Test
    void passwordsAreStoredAsBcryptHashesOnly() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE id = ?", String.class, admin.getId());
        assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void theSessionShowsWhoIsSignedIn() throws Exception {
        MockHttpSession session = login(mockMvc, admin.getUsername());
        mockMvc.perform(get(ME).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(admin.getUsername()))
                .andExpect(jsonPath("$.displayName").value(admin.getDisplayName()))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void usernamesAreCaseInsensitiveAndTrimmed() throws Exception {
        attempt("  " + admin.getUsername().toUpperCase() + " ", PASSWORD, randomAddress()).andExpect(status().isOk());
    }

    @Test
    void anAccountStoredInMixedCaseBeforeV5CanStillSignIn() throws Exception {
        String legacy = "Legacy-Owner-" + System.nanoTime();
        jdbc.update("INSERT INTO app_users (username, display_name, email, password_hash, role) VALUES (?, 'Legacy', lower(?) || '@test.local', ?, 'ADMIN')",
                legacy, legacy, encoder.encode(PASSWORD));
        attempt(legacy.toLowerCase(), PASSWORD, randomAddress()).andExpect(status().isOk());
        attempt(legacy, PASSWORD, randomAddress()).andExpect(status().isOk());
    }

    @Test
    void unknownUsersWrongPasswordsAndDisabledAccountsAllGetTheSameGenericAnswer() throws Exception {
        AppUser disabled = TestUsers.create(users, encoder, "auth-off", UserRole.ADMIN);
        disabled.setEnabled(false);
        users.saveAndFlush(disabled);

        String unknown = attempt("nobody-" + admin.getUsername(), PASSWORD, randomAddress())
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String wrong = attempt(admin.getUsername(), "not-the-password-1", randomAddress())
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        // the right password for a disabled account still fails, the same way
        String off = attempt(disabled.getUsername(), PASSWORD, randomAddress())
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(unknown).contains(GENERIC).isEqualTo(wrong).isEqualTo(off);
    }

    @Test
    void anOverlongPasswordIsAnOrdinaryFailureNotAServerError() throws Exception {
        attempt(admin.getUsername(), "x".repeat(150), randomAddress())
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value(GENERIC));
    }

    @Test
    void loginValidatesItsInputWithoutEchoingThePassword() throws Exception {
        mockMvc.perform(post(LOGIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void aSuccessfulLoginGetsANewSessionId() throws Exception {
        MockHttpSession before = new MockHttpSession();
        String oldId = before.getId();
        MvcResult result = mockMvc.perform(post(LOGIN).session(before).with(csrf()).with(from(randomAddress()))
                        // what the browser's JSESSIONID cookie does in a real container
                        .with(request -> {
                            request.setRequestedSessionId(oldId);
                            request.setRequestedSessionIdValid(true);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(admin.getUsername(), PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(oldId);
    }

    @Test
    void loginNeedsACsrfTokenAndTheSpaCookieFlowProvidesOne() throws Exception {
        // csrf() in other tests swaps the filter's token store for a test one; put the real cookie store back
        WebTestUtils.setCsrfTokenRepository(new MockHttpServletRequest(context.getServletContext()), csrfTokenRepository);
        String address = randomAddress();
        mockMvc.perform(post(LOGIN).with(from(address)).contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(admin.getUsername(), PASSWORD)))
                .andExpect(status().isForbidden());

        // GET /csrf sets the readable XSRF-TOKEN cookie; the app echoes it in X-XSRF-TOKEN
        MockHttpServletResponse csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse();
        Cookie token = csrf.getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        assertThat(token.isHttpOnly()).isFalse();

        mockMvc.perform(post(LOGIN).with(from(address)).cookie(token).header("X-XSRF-TOKEN", "forged")
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(admin.getUsername(), PASSWORD)))
                .andExpect(status().isForbidden());
        MvcResult ok = mockMvc.perform(post(LOGIN).with(from(address)).cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(admin.getUsername(), PASSWORD)))
                .andExpect(status().isOk()).andReturn();

        // a fresh token comes with the new session (the old cookie is cleared first, so take the last one)
        Cookie rotated = Arrays.stream(ok.getResponse().getCookies()).filter(c -> c.getName().equals("XSRF-TOKEN"))
                .reduce((first, second) -> second).orElseThrow();
        assertThat(rotated.getValue()).isNotEmpty().isNotEqualTo(token.getValue());
    }

    @Test
    void writesWithASessionButNoCsrfTokenAreRefused() throws Exception {
        MockHttpSession session = login(mockMvc, admin.getUsername());
        mockMvc.perform(post("/api/v1/suppliers").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CSRF Hardware\",\"phone\":\"0710000000\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM suppliers WHERE name = 'CSRF Hardware'", Long.class)).isZero();
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login(mockMvc, admin.getUsername());
        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get(ME)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutNeedsACsrfTokenSoAnotherSiteCantSignYouOut() throws Exception {
        MockHttpSession session = login(mockMvc, admin.getUsername());
        mockMvc.perform(post("/api/v1/auth/logout").session(session)).andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
        mockMvc.perform(get(ME).session(session)).andExpect(status().isOk());
    }

    @Test
    void anonymousCallersGetAJson401WithoutABasicAuthChallenge() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void securityHeadersAreSent() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void repeatedFailuresAreThrottledEvenIfTheRightPasswordFollows() throws Exception {
        String address = randomAddress();
        for (int i = 0; i < 5; i++) {
            attempt(admin.getUsername(), "wrong-password-" + i, address).andExpect(status().isUnauthorized());
        }
        attempt(admin.getUsername(), PASSWORD, randomAddress())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value(not(containsString(admin.getUsername()))));
        // an unknown username is throttled exactly the same way, so the limit reveals nothing
        String ghost = "ghost-" + admin.getUsername();
        for (int i = 0; i < 5; i++) {
            attempt(ghost, "wrong-password-" + i, randomAddress()).andExpect(status().isUnauthorized());
        }
        attempt(ghost, PASSWORD, randomAddress()).andExpect(status().isTooManyRequests());
    }

    @Test
    void oneAddressCantSprayManyUsernames() throws Exception {
        String address = randomAddress();
        for (int i = 0; i < 20; i++) {
            attempt("spray-" + i + "-" + admin.getUsername(), "wrong-password-1", address).andExpect(status().isUnauthorized());
        }
        attempt(admin.getUsername(), PASSWORD, address).andExpect(status().isTooManyRequests());
        attempt(admin.getUsername(), PASSWORD, randomAddress()).andExpect(status().isOk());
    }

    @Test
    void signInsAndSignOutsAreAuditedWithoutCredentials() throws Exception {
        attempt(admin.getUsername(), "wrong-password-1", randomAddress()).andExpect(status().isUnauthorized());
        MockHttpSession session = login(mockMvc, admin.getUsername());
        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());

        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT event_type, actor_user_id, target_user_id, username, remote_address, details FROM security_events "
                        + "WHERE username = ? ORDER BY id", admin.getUsername());
        assertThat(events).extracting(e -> e.get("event_type")).containsExactly("LOGIN_FAILED", "LOGIN_SUCCEEDED", "LOGOUT");
        assertThat(events.get(0).get("actor_user_id")).isNull();
        assertThat(events.get(1).get("actor_user_id")).isEqualTo(admin.getId());
        assertThat(events).allSatisfy(e -> assertThat(e.get("remote_address")).isNotNull());
        assertThat(events.toString()).doesNotContain(PASSWORD).doesNotContain("wrong-password-1");
    }

    @Test
    void aUserCanChangeTheirOwnPasswordAndTheirOtherSessionsEnd() throws Exception {
        MockHttpSession phone = login(mockMvc, admin.getUsername());
        MockHttpSession laptop = login(mockMvc, admin.getUsername());
        String newPassword = "a-brand-new-password-2";

        changePassword(laptop, "not-my-password-0", newPassword).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The current password is incorrect."));
        changePassword(laptop, PASSWORD, "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        changePassword(laptop, PASSWORD, PASSWORD).andExpect(status().isUnprocessableContent());
        changePassword(laptop, PASSWORD, newPassword).andExpect(status().isNoContent());

        mockMvc.perform(get(ME).session(laptop)).andExpect(status().isOk());
        mockMvc.perform(get(ME).session(phone)).andExpect(status().isUnauthorized());
        assertThat(phone.isInvalid()).isTrue();

        attempt(admin.getUsername(), PASSWORD, randomAddress()).andExpect(status().isUnauthorized());
        login(mockMvc, admin.getUsername(), newPassword);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM security_events WHERE event_type = 'PASSWORD_CHANGED' AND target_user_id = ?",
                Long.class, admin.getId())).isOne();
    }

    @Test
    void recordsAreAttributedToTheSignedInUser() throws Exception {
        MockHttpSession session = login(mockMvc, admin.getUsername());
        String json = mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Attributed Hardware\",\"phone\":\"0710000000\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT created_by FROM suppliers WHERE id = ?", Long.class, id)).isEqualTo(admin.getId());
    }

    @Test
    void thereIsNoPublicSignUp() throws Exception {
        for (String path : List.of("/api/v1/auth/register", "/api/v1/auth/signup", "/api/v1/users", "/api/v1/register")) {
            mockMvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"intruder\",\"password\":\"intruder-password-1\",\"role\":\"SUPER_ADMIN\"}"))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users WHERE username = 'intruder'", Long.class)).isZero();
    }

    private ResultActions attempt(String username, String password, String address) throws Exception {
        return mockMvc.perform(post(LOGIN).with(csrf()).with(from(address)).contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(username, password)));
    }

    private ResultActions changePassword(MockHttpSession session, String current, String next) throws Exception {
        return mockMvc.perform(put("/api/v1/auth/me/password").session(session).with(csrf()).with(from(randomAddress()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }
}
