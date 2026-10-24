package com.sondurukedella.suppliers.controller;

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

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.suppliers.dto.SupplierResponse;
import com.sondurukedella.suppliers.service.SupplierService;
import java.time.OffsetDateTime;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller slice only - the service is mocked. Security, binding, validation and status codes.
@WebMvcTest(controllers = SupplierController.class)
class SupplierControllerWebTest {

    private static final String URL = "/api/v1/suppliers";
    private static final String SUPPLIER = """
            {"name":"Mihintale Hardware","phone":"025 222 3344","supplies":"Cement, steel"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupplierService service;

    @Test
    void anonymousUsersAreRejected() throws Exception {
        mockMvc.perform(get(URL).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/1").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void writesNeedACsrfToken() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(SUPPLIER)).andExpect(status().isForbidden());
        mockMvc.perform(put(URL + "/1").contentType(MediaType.APPLICATION_JSON).content(SUPPLIER)).andExpect(status().isForbidden());
        mockMvc.perform(put(URL + "/1/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false,\"version\":0}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void nameAndPhoneAreRequiredAndEverythingElseIsOptional() throws Exception {
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \",\"phone\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("phone")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("Enter the supplier name.")))
                .andExpect(jsonPath("$.errors.length()").value(2));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void lengthsEmailAndCreditDaysAreChecked() throws Exception {
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","phone":"%s","contactPerson":"%s","email":"not-an-email","address":"%s",
                         "supplies":"%s","creditDays":-1}""".formatted("n".repeat(201), "1".repeat(31), "c".repeat(151),
                        "a".repeat(301), "s".repeat(301))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", Matchers.hasItems("name", "phone", "contactPerson", "email",
                        "address", "supplies", "creditDays")));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void createIs201WithALocation() throws Exception {
        when(service.create(any())).thenReturn(response(7L, true));

        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith("/api/v1/suppliers/7")))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser
    void updatesAndStatusChangesNeedAVersion() throws Exception {
        mockMvc.perform(put(URL + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SUPPLIER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", Matchers.hasItems("version", "creditDays")));
        mockMvc.perform(put(URL + "/1/status").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", Matchers.hasItems("active", "version")));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    void serviceErrorsMapTo404And409() throws Exception {
        when(service.get(9L)).thenThrow(new NotFoundException("Supplier not found."));
        when(service.setActive(eq(1L), any())).thenThrow(new ConflictException("The record was changed by someone else."));

        mockMvc.perform(get(URL + "/9")).andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Supplier not found."));
        mockMvc.perform(put(URL + "/1/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false,\"version\":0}")).andExpect(status().isConflict());
        mockMvc.perform(get(URL + "/abc")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void listPassesSearchAndActiveFilter() throws Exception {
        when(service.search(any(), any(), any(Pageable.class))).thenReturn(new PageResponse<>(List.of(response(1L, true)), 0, 20, 1, 1));

        mockMvc.perform(get(URL).param("q", "hardware").param("active", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name").value("Mihintale Hardware"));
        verify(service).search(eq("hardware"), eq(true), argThat(p -> p.getPageSize() == 20));
        mockMvc.perform(get(URL).param("active", "maybe")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void suppliersAreNeverDeleted() throws Exception {
        mockMvc.perform(delete(URL + "/1").with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    private static SupplierResponse response(long id, boolean active) {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-24T10:00:00+05:30");
        return new SupplierResponse(id, "Mihintale Hardware", null, "025 222 3344", null, null, "Cement, steel", 14,
                active, now, now, 0L);
    }
}
