package com.sondurukedella.integration.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// End-to-end against a real database. @Transactional rolls each test back so nothing is left behind.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser
class ClientApiIntegrationTest {

    private static final String CLIENTS = "/api/v1/clients";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aClientCanBeCreatedReadAndUpdated() throws Exception {
        long id = create("""
                {"name":"  Sanjeewa Rathnayake ","kind":"INDIVIDUAL","phone":"070 221 6684",
                 "email":"","address":"No. 7, Jaya Mawatha","city":"Anuradhapura","notes":"  "}""");

        mockMvc.perform(get(CLIENTS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sanjeewa Rathnayake"))
                .andExpect(jsonPath("$.kind").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.phone").value("070 221 6684"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(put(CLIENTS + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Sanjeewa Rathnayake","kind":"BUSINESS","phone":"070 221 6684",
                         "email":"sanjeewa.r@yahoo.com","address":"No. 7, Jaya Mawatha","city":"Mihintale",
                         "notes":"Prefers bank transfer.","version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("BUSINESS"))
                .andExpect(jsonPath("$.email").value("sanjeewa.r@yahoo.com"))
                .andExpect(jsonPath("$.city").value("Mihintale"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void aStaleUpdateIsAConflictAndLeavesTheClientUnchanged() throws Exception {
        long id = create("{\"name\":\"K. M. Bandara\",\"kind\":\"INDIVIDUAL\",\"phone\":\"077 512 9043\"}");
        String update = """
                {"name":"%s","kind":"INDIVIDUAL","phone":"077 512 9043","version":0}""";

        mockMvc.perform(put(CLIENTS + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(update.formatted("K. M. Bandara (Nuwarawewa)")))
                .andExpect(status().isOk());
        mockMvc.perform(put(CLIENTS + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(update.formatted("Overwritten")))
                .andExpect(status().isConflict());

        mockMvc.perform(get(CLIENTS + "/" + id))
                .andExpect(jsonPath("$.name").value("K. M. Bandara (Nuwarawewa)"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void invalidInputIsRejectedAndNothingIsSaved() throws Exception {
        mockMvc.perform(post(CLIENTS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Invalid Phone Client\",\"kind\":\"INDIVIDUAL\",\"phone\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("phone")));

        mockMvc.perform(get(CLIENTS).param("q", "Invalid Phone Client"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void theDirectoryIsSearchableFilterableAndPaged() throws Exception {
        create("{\"name\":\"Zz Test Alpha\",\"kind\":\"INDIVIDUAL\",\"phone\":\"071 000 0001\",\"city\":\"Zzville\"}");
        create("{\"name\":\"Zz Test Beta\",\"kind\":\"BUSINESS\",\"phone\":\"071 000 0002\",\"city\":\"Zzville\"}");
        create("{\"name\":\"Zz Test Gamma\",\"kind\":\"INDIVIDUAL\",\"phone\":\"071 000 0003\",\"city\":\"Zzville\"}");

        mockMvc.perform(get(CLIENTS).param("q", "zzVILLE").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Zz Test Alpha"))
                .andExpect(jsonPath("$.content[1].name").value("Zz Test Beta"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get(CLIENTS).param("q", "zzville").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content[*].name").value(hasItem("Zz Test Gamma")))
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get(CLIENTS).param("q", "zzville").param("kind", "INDIVIDUAL").param("sort", "name,desc"))
                .andExpect(jsonPath("$.content[0].name").value("Zz Test Gamma"))
                .andExpect(jsonPath("$.content[*].name").value(not(hasItem("Zz Test Beta"))));

        mockMvc.perform(get(CLIENTS).param("q", "071 000 0002"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].kind").value("BUSINESS"));
    }

    @Test
    void unsupportedPagingIsABadRequest() throws Exception {
        mockMvc.perform(get(CLIENTS).param("sort", "notes")).andExpect(status().isBadRequest());
        mockMvc.perform(get(CLIENTS).param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownClientsAreNotFound() throws Exception {
        mockMvc.perform(get(CLIENTS + "/" + Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Client not found."));
        mockMvc.perform(put(CLIENTS + "/" + Long.MAX_VALUE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"kind\":\"INDIVIDUAL\",\"phone\":\"0710000000\",\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get(CLIENTS).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(CLIENTS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"kind\":\"INDIVIDUAL\",\"phone\":\"0710000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    private long create(String body) throws Exception {
        String json = mockMvc.perform(post(CLIENTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
