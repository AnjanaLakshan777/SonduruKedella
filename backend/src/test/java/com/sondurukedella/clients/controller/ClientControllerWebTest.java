package com.sondurukedella.clients.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sondurukedella.clients.dto.ClientResponse;
import com.sondurukedella.clients.entity.ClientKind;
import com.sondurukedella.clients.service.ClientService;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.web.PageResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller slice only - the service is mocked, so these tests just cover security, validation and status codes.
@WebMvcTest(controllers = ClientController.class)
class ClientControllerWebTest {

    private static final String CLIENTS = "/api/v1/clients";
    private static final String VALID_CLIENT = """
            {"name":"Dilani Jayawardena","kind":"INDIVIDUAL","phone":"071 438 2291",
             "email":"dilani.j@gmail.com","address":"No. 18, Mihintale Road","city":"Anuradhapura"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientService clientService;

    @Test
    void anonymousUsersCannotReadClients() throws Exception {
        mockMvc.perform(get(CLIENTS).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(CLIENTS + "/1").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        verifyNoInteractions(clientService);
    }

    @Test
    void anonymousUsersCannotChangeClients() throws Exception {
        mockMvc.perform(post(CLIENTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(CLIENTS + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(withVersion(VALID_CLIENT)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser
    void changesWithoutACsrfTokenAreRejected() throws Exception {
        mockMvc.perform(post(CLIENTS).contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(CLIENTS + "/1").contentType(MediaType.APPLICATION_JSON).content(withVersion(VALID_CLIENT)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser
    void createReturns201WithTheLocationOfTheNewClient() throws Exception {
        when(clientService.create(any())).thenReturn(response(42L, 0L));

        mockMvc.perform(post(CLIENTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/clients/42")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.kind").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @WithMockUser
    void createRequiresANameAPhoneAndAKind() throws Exception {
        mockMvc.perform(post(CLIENTS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"phone\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("phone")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("kind")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("Enter the client’s name.")));
        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser
    void createRejectsAnInvalidPhoneOrEmail() throws Exception {
        mockMvc.perform(post(CLIENTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"A Client","kind":"BUSINESS","phone":"071-438-2291","email":"name@host"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("phone")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("Enter a phone number, e.g. 071 234 5678.")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("Check the email address.")));
        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser
    void blankOptionalFieldsAreAccepted() throws Exception {
        when(clientService.create(any())).thenReturn(response(43L, 0L));

        mockMvc.perform(post(CLIENTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"A Client","kind":"INDIVIDUAL","phone":"0712345678",
                         "email":"   ","address":"","city":"","notes":""}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void createRejectsTextLongerThanTheColumns() throws Exception {
        mockMvc.perform(post(CLIENTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","kind":"INDIVIDUAL","phone":"0712345678","city":"%s","address":"%s"}"""
                        .formatted("n".repeat(201), "c".repeat(101), "a".repeat(301))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("city")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("address")));
        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser
    void anUnknownClientTypeIsABadRequest() throws Exception {
        mockMvc.perform(post(CLIENTS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CLIENT.replace("INDIVIDUAL", "Individual")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLIENTS).param("kind", "PARTNER")).andExpect(status().isBadRequest());
        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser
    void updatesWithoutAVersionAreRejected() throws Exception {
        mockMvc.perform(put(CLIENTS + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("version")));
        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser
    void staleUpdatesAreAConflict() throws Exception {
        when(clientService.update(eq(1L), any())).thenThrow(new ConflictException("The record was changed."));

        mockMvc.perform(put(CLIENTS + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(withVersion(VALID_CLIENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @WithMockUser
    void unknownClientsReturnAProblemDetail404() throws Exception {
        when(clientService.get(7L)).thenThrow(new NotFoundException("Client not found."));

        mockMvc.perform(get(CLIENTS + "/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Client not found."));
    }

    @Test
    @WithMockUser
    void aNonNumericIdIsABadRequest() throws Exception {
        mockMvc.perform(get(CLIENTS + "/c1")).andExpect(status().isBadRequest());
        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser
    void listPassesSearchFilterAndPagingToTheService() throws Exception {
        when(clientService.search(eq("anu"), eq(ClientKind.BUSINESS), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(response(3L, 1L)), 1, 5, 6, 2));

        mockMvc.perform(get(CLIENTS).param("q", "anu").param("kind", "BUSINESS")
                        .param("page", "1").param("size", "5").param("sort", "city,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(clientService).search(eq("anu"), eq(ClientKind.BUSINESS),
                argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 5
                        && p.getSort().getOrderFor("city") != null && p.getSort().getOrderFor("city").isDescending()));
    }

    @Test
    @WithMockUser
    void clientsCannotBeDeleted() throws Exception {
        mockMvc.perform(delete(CLIENTS + "/1").with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(clientService);
    }

    private static String withVersion(String json) {
        return json.substring(0, json.lastIndexOf('}')) + ",\"version\":0}";
    }

    private static ClientResponse response(long id, long version) {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-24T09:30:00+05:30");
        return new ClientResponse(id, "Dilani Jayawardena", ClientKind.INDIVIDUAL, "071 438 2291",
                "dilani.j@gmail.com", "No. 18, Mihintale Road", "Anuradhapura", null, now, now, version);
    }
}
