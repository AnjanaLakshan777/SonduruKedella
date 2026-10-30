package com.sondurukedella.integration.api;

import static com.sondurukedella.support.TestUsers.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sondurukedella.security.user.AppUserRepository;
import com.sondurukedella.security.user.UserRole;
import com.sondurukedella.support.TestUsers;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// Hits every endpoint Spring MVC knows about (so new ones get checked automatically) as anonymous,
// Admin and Super Admin. Path ids are 999999999 so nothing is found or changed - we only care about
// the security response. Rolls back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EndpointAuthorizationIntegrationTest {

    private static final Set<String> PUBLIC = Set.of("POST /api/v1/auth/login", "GET /api/v1/auth/csrf");
    // Super Admin only even though it's not under /api/v1/users
    private static final Set<String> SUPER_ADMIN_ONLY = Set.of("PUT /api/v1/settings/commission-policy");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private MockHttpSession adminSession;
    private MockHttpSession superSession;

    @BeforeEach
    void setUp() throws Exception {
        adminSession = login(mockMvc, TestUsers.create(users, encoder, "matrix-admin", UserRole.ADMIN).getUsername());
        superSession = login(mockMvc, TestUsers.create(users, encoder, "matrix-root", UserRole.SUPER_ADMIN).getUsername());
    }

    @Test
    void everyEndpointFollowsThePermissionPolicy() throws Exception {
        List<String[]> endpoints = endpoints();
        // sanity check that the scan really found the modules
        assertThat(endpoints).hasSizeGreaterThan(100);
        assertThat(endpoints).extracting(e -> e[1]).contains("/api/v1/payroll/weeks/{weekStart}/employees/{employeeId}/payments",
                "/api/v1/inventory/issues", "/api/v1/users/{id}/role", "/api/v1/settings/units/{id}",
                "/api/v1/commissions/{id}/payments", "/api/v1/projects/{projectId}/commissions");

        List<String> problems = new ArrayList<>();
        for (String[] endpoint : endpoints) {
            String method = endpoint[0];
            String pattern = endpoint[1];
            String path = concrete(pattern);
            String key = method + " " + pattern;
            boolean superAdminOnly = pattern.startsWith("/api/v1/users") || SUPER_ADMIN_ONLY.contains(key);

            int anonymous = statusOf(method, path, null);
            if (PUBLIC.contains(key) ? isAuthFailure(anonymous) : anonymous != 401) {
                problems.add(key + " anonymous -> " + anonymous);
            }
            int asAdmin = statusOf(method, path, adminSession);
            if (superAdminOnly ? asAdmin != 403 : isAuthFailure(asAdmin)) {
                problems.add(key + " ADMIN -> " + asAdmin);
            }
            int asSuperAdmin = statusOf(method, path, superSession);
            if (isAuthFailure(asSuperAdmin)) {
                problems.add(key + " SUPER_ADMIN -> " + asSuperAdmin);
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void methodsWithoutAHandlerAreStillProtected() throws Exception {
        // DELETE/PATCH on paths that only have GET/PUT must not slip through as anonymous
        mockMvc.perform(delete("/api/v1/projects/1").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/suppliers/1").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/users/1").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/users/1").session(adminSession).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/projects/1").session(adminSession).with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownPathsAndActuatorAreNotOpen() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/anything-else")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/anything-else").session(superSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/info").session(adminSession)).andExpect(status().isForbidden());
    }

    @Test
    void writesWithoutACsrfTokenAreRefusedForEveryRole() throws Exception {
        for (MockHttpSession session : List.of(adminSession, superSession)) {
            mockMvc.perform(post("/api/v1/projects").session(session)).andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/settings/company").session(session)).andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/settings/units/1").session(session)).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/users").session(superSession)).andExpect(status().isForbidden());
    }

    private List<String[]> endpoints() {
        List<String[]> endpoints = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                assertThat(methods).as("every API mapping names its HTTP method: " + pattern).isNotEmpty();
                methods.forEach(m -> endpoints.add(new String[] {m.name(), pattern}));
            }
        }
        return endpoints;
    }

    private int statusOf(String method, String path, MockHttpSession session) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path).with(csrf());
        if (session != null) {
            builder.session(session);
        }
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }

    private static boolean isAuthFailure(int status) {
        return status == 401 || status == 403;
    }

    private static String concrete(String pattern) {
        return pattern.replaceAll("\\{(weekStart|date)}", "not-a-date").replaceAll("\\{[^}]+}", "999999999");
    }
}
