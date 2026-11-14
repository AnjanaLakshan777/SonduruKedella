package com.sondurukedella.integration.api;

import static com.sondurukedella.support.TestUsers.from;
import static com.sondurukedella.support.TestUsers.login;
import static com.sondurukedella.support.TestUsers.randomAddress;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// A user the Super Admin adds sets their own password on the first sign-in, and only then.
// Until they do, the API answers 403 to everything but /auth/me, the password change and sign-out.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FirstSignInApiIntegrationTest {

    private static final String STARTING = "starting-password-1";
    private static final String CHOSEN = "my-own-password-12";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private MockHttpSession superSession;
    private String username;
    private long id;

    @BeforeEach
    void setUp() throws Exception {
        AppUser superAdmin = TestUsers.create(users, encoder, "first-root", UserRole.SUPER_ADMIN);
        superSession = login(mockMvc, superAdmin.getUsername());
        username = "newbie-" + System.nanoTime();
        String json = mockMvc.perform(post("/api/v1/users").session(superSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"displayName\":\"Newbie\",\"email\":\"%s@example.com\",\"password\":\"%s\"}"
                                .formatted(username, username, STARTING)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        id = ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void theFirstSignInOnlyOpensThePasswordChange() throws Exception {
        MockHttpSession session = login(mockMvc, username, STARTING);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        mockMvc.perform(get("/api/v1/projects").session(session))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value("Set your own password before you continue."));
        mockMvc.perform(get("/api/v1/settings").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/clients").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // the same password again isn't a change
        changePassword(session, STARTING, STARTING).andExpect(status().isUnprocessableContent());

        changePassword(session, STARTING, CHOSEN).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
        mockMvc.perform(get("/api/v1/projects").session(session)).andExpect(status().isOk());
    }

    @Test
    void afterThatEverySignInIsNormal() throws Exception {
        changePassword(login(mockMvc, username, STARTING), STARTING, CHOSEN).andExpect(status().isNoContent());

        MockHttpSession next = login(mockMvc, username, CHOSEN);
        mockMvc.perform(get("/api/v1/auth/me").session(next))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
        mockMvc.perform(get("/api/v1/projects").session(next)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/" + id).session(superSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void theyCanSignOutWithoutChangingIt() throws Exception {
        MockHttpSession session = login(mockMvc, username, STARTING);
        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void usersWhoAlreadyExistAreNotAsked() throws Exception {
        AppUser existing = TestUsers.create(users, encoder, "first-old", UserRole.ADMIN);
        MockHttpSession session = login(mockMvc, existing.getUsername());
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
        mockMvc.perform(get("/api/v1/projects").session(session)).andExpect(status().isOk());
    }

    private ResultActions changePassword(MockHttpSession session, String current, String next)
            throws Exception {
        return mockMvc.perform(put("/api/v1/auth/me/password").session(session).with(csrf()).with(from(randomAddress()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
    }
}
