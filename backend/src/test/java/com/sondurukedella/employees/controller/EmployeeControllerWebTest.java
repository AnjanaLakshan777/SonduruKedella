package com.sondurukedella.employees.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.employees.service.AttendanceService;
import com.sondurukedella.employees.service.EmployeeService;
import com.sondurukedella.employees.service.OvertimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Controller slices only - services are mocked. Security, binding, validation and status codes.
@WebMvcTest(controllers = {EmployeeController.class, AttendanceController.class})
class EmployeeControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employees;
    @MockitoBean
    private AttendanceService attendance;
    @MockitoBean
    private OvertimeService overtime;

    @Test
    void anonymousUsersAreRejected() throws Exception {
        for (String path : new String[] {"/api/v1/employees", "/api/v1/employees/1", "/api/v1/attendance?from=2026-09-07&to=2026-09-07",
                "/api/v1/overtime?from=2026-09-07&to=2026-09-07"}) {
            mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(employees, attendance, overtime);
    }

    @Test
    @WithMockUser
    void everyWriteNeedsACsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/employees/1").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/attendance/2026-09-07").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/overtime").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(employees, attendance, overtime);
    }

    @Test
    @WithMockUser
    void requestsAreValidatedBeforeTheServices() throws Exception {
        mockMvc.perform(post("/api/v1/employees").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"dailyWage\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("name", "role", "dailyWage", "overtimeRate", "joinedOn")));
        mockMvc.perform(put("/api/v1/employees/1/status").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"GONE\",\"version\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/attendance/2026-09-07").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\":[{\"employeeId\":1}]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("entries[0].status")));
        mockMvc.perform(post("/api/v1/overtime").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"hours\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("employeeId", "date", "hours", "recordedBy")));
        mockMvc.perform(get("/api/v1/attendance").param("from", "2026-02-30").param("to", "2026-03-01")).andExpect(status().isBadRequest());
        verifyNoInteractions(employees, attendance, overtime);
    }

    @Test
    @WithMockUser
    void serviceErrorsMapTo404And409() throws Exception {
        when(employees.get(9L)).thenThrow(new NotFoundException("Employee not found."));
        when(attendance.saveDay(any(), any())).thenThrow(new ConflictException("Kamal's week of 2026-09-07 has been paid, so it can't change."));
        mockMvc.perform(get("/api/v1/employees/9")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/attendance/2026-09-07").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"entries\":[]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Kamal's week of 2026-09-07 has been paid, so it can't change."));
    }

    @Test
    @WithMockUser
    void employeesAreNeverDeleted() throws Exception {
        mockMvc.perform(delete("/api/v1/employees/1").with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(employees, attendance, overtime);
    }
}
