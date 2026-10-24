package com.sondurukedella.integration.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// Setup through the real API, signed in as a setup user so it also works under @WithAnonymousUser.
final class ApiFixtures {

    private ApiFixtures() {
    }

    static long project(MockMvc mockMvc, long clientId, String type, String name) throws Exception {
        String json = mockMvc.perform(post("/api/v1/projects").with(user("setup")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"name":"%s","type":"%s","clientId":%d,"contractValue":100000,"startDate":"2026-01-01",
                         "expectedCompletion":"2026-12-31","schedule":[{"description":"All","dueDate":"2026-12-31","amount":100000}]}"""
                        .formatted(name, type, clientId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    static void setProjectStatus(MockMvc mockMvc, long projectId, String projectStatus, long version) throws Exception {
        mockMvc.perform(put("/api/v1/projects/" + projectId + "/status").with(user("setup")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\",\"version\":%d}".formatted(projectStatus, version)))
                .andExpect(status().isOk());
    }
}
