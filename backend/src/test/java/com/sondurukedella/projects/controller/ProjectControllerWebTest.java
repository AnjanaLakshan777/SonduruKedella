package com.sondurukedella.projects.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.projects.dto.ProjectResponse;
import com.sondurukedella.projects.entity.ProjectStatus;
import com.sondurukedella.projects.entity.ProjectType;
import com.sondurukedella.projects.service.ProjectService;
import java.math.BigDecimal;
import java.time.LocalDate;
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

// Controller slice only - the service is mocked, so this covers security, binding, validation and status codes.
@WebMvcTest(controllers = ProjectController.class)
class ProjectControllerWebTest {

    private static final String PROJECTS = "/api/v1/projects";
    private static final String VALID_PROJECT = """
            {"name":"Nuwarawewa Lakeside Villa","type":"CONSTRUCTION","clientId":2,"contractValue":1000000,
             "startDate":"2026-11-01","expectedCompletion":"2027-09-01",
             "schedule":[{"description":"Advance","dueDate":"2026-11-01","amount":400000},
                         {"description":"Handover","dueDate":"2027-09-01","amount":600000}]}""";
    private static final String STATUS_BODY = "{\"status\":\"COMPLETED\",\"version\":0}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @Test
    void anonymousUsersGet401Everywhere() throws Exception {
        mockMvc.perform(get(PROJECTS).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROJECTS + "/1").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PROJECTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_PROJECT))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(PROJECTS + "/1/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(STATUS_BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(projectService);
    }

    @Test
    @WithMockUser
    void writesWithoutACsrfTokenAreForbidden() throws Exception {
        mockMvc.perform(post(PROJECTS).contentType(MediaType.APPLICATION_JSON).content(VALID_PROJECT))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(PROJECTS + "/1/status").contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(projectService);
    }

    @Test
    @WithMockUser
    void createReturns201AndTheServerAssignedCode() throws Exception {
        when(projectService.create(any())).thenReturn(response(ProjectStatus.PLANNING));

        mockMvc.perform(post(PROJECTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_PROJECT))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/projects/5")))
                .andExpect(jsonPath("$.code").value("PRJ-2026-005"))
                .andExpect(jsonPath("$.clientName").value("K. M. Bandara"))
                .andExpect(jsonPath("$.type").value("CONSTRUCTION"));
    }

    @Test
    @WithMockUser
    void requiredFieldsAreReportedPerField() throws Exception {
        mockMvc.perform(post(PROJECTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("type")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("contractValue")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("startDate")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("expectedCompletion")));
        verifyNoInteractions(projectService);
    }

    @Test
    @WithMockUser
    void moneyWithMoreThanTwoDecimalsIsRejected() throws Exception {
        mockMvc.perform(post(PROJECTS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PROJECT.replace("\"contractValue\":1000000", "\"contractValue\":1000000.001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("contractValue")));
        verifyNoInteractions(projectService);
    }

    @Test
    @WithMockUser
    void scheduleRowsDelayTermsAndTheInlineClientAreValidatedPerField() throws Exception {
        mockMvc.perform(post(PROJECTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Villa","type":"CONSTRUCTION","contractValue":1000,
                         "startDate":"2026-11-01","expectedCompletion":"2027-09-01",
                         "newClient":{"name":"","kind":"INDIVIDUAL","phone":"12"},
                         "schedule":[{"description":"","dueDate":null,"amount":0}],
                         "delayTerms":{"rule":"PER_DAY","ratePerDay":-5,"graceDays":-1}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("newClient.name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("newClient.phone")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("schedule[0].description")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("schedule[0].dueDate")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("schedule[0].amount")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("delayTerms.contractDeadline")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("delayTerms.ratePerDay")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("delayTerms.graceDays")));
        verifyNoInteractions(projectService);
    }

    @Test
    @WithMockUser
    void businessRuleFailuresFromTheServiceAreProblemDetails() throws Exception {
        when(projectService.create(any())).thenThrow(new InvalidRequestException(
                "The schedule adds up to 900000.00 but the contract value is 1000000.00. They must match exactly."));

        mockMvc.perform(post(PROJECTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_PROJECT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("must match exactly")));
    }

    @Test
    @WithMockUser
    void unknownEnumValuesAreBadRequests() throws Exception {
        // the API uses constants; the UI's display labels have to be mapped
        mockMvc.perform(post(PROJECTS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PROJECT.replace("CONSTRUCTION", "Construction")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PROJECTS).param("status", "In Progress")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PROJECTS).param("type", "RENOVATION")).andExpect(status().isBadRequest());
        mockMvc.perform(put(PROJECTS + "/1/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARCHIVED\",\"version\":0}")).andExpect(status().isBadRequest());
        verifyNoInteractions(projectService);
    }

    @Test
    @WithMockUser
    void malformedFiltersAreBadRequests() throws Exception {
        mockMvc.perform(get(PROJECTS).param("startFrom", "2026-13-01")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PROJECTS).param("dueTo", "next year")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PROJECTS).param("clientId", "c1")).andExpect(status().isBadRequest());
        verifyNoInteractions(projectService);
    }

    @Test
    @WithMockUser
    void listPassesFiltersAndDefaultsToTenPerPage() throws Exception {
        when(projectService.search(any(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(response(ProjectStatus.IN_PROGRESS)), 0, 10, 1, 1));

        mockMvc.perform(get(PROJECTS).param("q", "villa").param("type", "CONSTRUCTION").param("status", "IN_PROGRESS")
                        .param("clientId", "2").param("startFrom", "2026-01-01").param("startTo", "2026-12-31")
                        .param("dueFrom", "2027-01-01").param("dueTo", "2027-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.size").value(10));

        verify(projectService).search(
                argThat(f -> "villa".equals(f.q()) && f.type() == ProjectType.CONSTRUCTION
                        && f.status() == ProjectStatus.IN_PROGRESS && f.clientId() == 2L
                        && LocalDate.of(2026, 1, 1).equals(f.startFrom()) && LocalDate.of(2026, 12, 31).equals(f.startTo())
                        && LocalDate.of(2027, 1, 1).equals(f.dueFrom()) && LocalDate.of(2027, 12, 31).equals(f.dueTo())),
                argThat(p -> p.getPageSize() == 10 && p.getPageNumber() == 0));
    }

    @Test
    @WithMockUser
    void statusUpdatesNeedAStatusAndAVersion() throws Exception {
        mockMvc.perform(put(PROJECTS + "/1/status").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("status")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("version")));
        verifyNoInteractions(projectService);
    }

    @Test
    @WithMockUser
    void statusUpdatesReturnTheProject() throws Exception {
        when(projectService.changeStatus(eq(5L), any())).thenReturn(response(ProjectStatus.COMPLETED));

        mockMvc.perform(put(PROJECTS + "/5/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(STATUS_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser
    void staleStatusUpdatesAreConflicts() throws Exception {
        when(projectService.changeStatus(eq(5L), any())).thenThrow(new ConflictException("Changed by someone else."));

        mockMvc.perform(put(PROJECTS + "/5/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(STATUS_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @WithMockUser
    void missingProjectsAre404() throws Exception {
        when(projectService.get(9L)).thenThrow(new NotFoundException("Project not found."));

        mockMvc.perform(get(PROJECTS + "/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Project not found."));
        mockMvc.perform(get(PROJECTS + "/PRJ-2026-001")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void thereIsNoDeleteOrGeneralEdit() throws Exception {
        mockMvc.perform(delete(PROJECTS + "/1").with(csrf())).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put(PROJECTS + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_PROJECT))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch(PROJECTS + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(projectService);
    }

    private static ProjectResponse response(ProjectStatus status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-24T09:30:00+05:30");
        return new ProjectResponse(5L, "PRJ-2026-005", "Nuwarawewa Lakeside Villa", ProjectType.CONSTRUCTION, 2L,
                "K. M. Bandara", "Nuwarawewa", null, new BigDecimal("1000000.00"), LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 9, 1), null, status, null, now, now, 0L);
    }
}
