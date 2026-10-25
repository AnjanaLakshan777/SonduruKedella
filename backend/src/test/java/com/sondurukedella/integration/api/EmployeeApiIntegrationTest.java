package com.sondurukedella.integration.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Employees, attendance and overtime against the real schema. Each test rolls back.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class EmployeeApiIntegrationTest {

    // a past Monday-to-Saturday week, so no date is in the future
    private static final LocalDate MON = LocalDate.of(2026, 9, 7);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long house;

    @BeforeEach
    void setUp() throws Exception {
        long client = jdbc.queryForObject("INSERT INTO clients (name, phone) VALUES ('Labour Client', '0710000000') RETURNING id", Long.class);
        house = ApiFixtures.project(mockMvc, client, "CONSTRUCTION", "House");
        ApiFixtures.setProjectStatus(mockMvc, house, "IN_PROGRESS", 0);
    }

    @Test
    void anEmployeeIsCreatedReadAndUpdated() throws Exception {
        long id = id(create("  Sunil Perera ", "Mason", "3500", "660").andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sunil Perera"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.dailyWage").value(3500.0))
                .andExpect(jsonPath("$.phone").doesNotExist()));
        mockMvc.perform(get("/api/v1/employees/" + id)).andExpect(jsonPath("$.overtimeRate").value(660.0));
        mockMvc.perform(put("/api/v1/employees/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Sunil Perera","role":"Head mason","phone":" 0771234567 ","dailyWage":4000,"overtimeRate":750,
                         "joinedOn":"2026-01-05","version":0}"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("Head mason"))
                .andExpect(jsonPath("$.phone").value("0771234567")).andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(put("/api/v1/employees/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"X","role":"Y","dailyWage":1,"overtimeRate":1,"joinedOn":"2026-01-05","version":0}"""))
                .andExpect(status().isConflict()); // stale
        mockMvc.perform(get("/api/v1/employees/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void namesRatesAndTheJoiningDateAreRequired() throws Exception {
        mockMvc.perform(post("/api/v1/employees").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"dailyWage\":0,\"overtimeRate\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", Matchers.hasItems("name", "role", "dailyWage", "overtimeRate", "joinedOn")));
        create("A", "B", "3500.001", "660").andExpect(status().isBadRequest());
    }

    @Test
    void employeesAreSearchedFilteredAndDeactivatedNotDeleted() throws Exception {
        long a = id(create("Zz Kamal", "Labourer", "3000", "560"));
        id(create("Zz Nimal", "Carpenter", "3800", "710"));
        mockMvc.perform(put("/api/v1/employees/" + a + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\",\"version\":0}")).andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/employees").param("q", "zz")).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/employees").param("q", "zz").param("status", "ACTIVE"))
                .andExpect(jsonPath("$.content[*].name").value(Matchers.contains("Zz Nimal")));
        mockMvc.perform(get("/api/v1/employees").param("role", "LABOURER")).andExpect(jsonPath("$.content[*].name").value(Matchers.hasItem("Zz Kamal")));
        mockMvc.perform(get("/api/v1/employees").param("q", "zz").param("sort", "dailyWage,desc"))
                .andExpect(jsonPath("$.content[0].name").value("Zz Nimal"));
        mockMvc.perform(get("/api/v1/employees").param("sort", "phone")).andExpect(status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/employees/" + a).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void theDayRegisterSavesAndReplacesADay() throws Exception {
        long a = id(create("Kamal", "Labourer", "3000", "560"));
        long b = id(create("Nimal", "Mason", "3500", "660"));
        saveDay(MON, "[{\"employeeId\":%d,\"status\":\"PRESENT\",\"projectId\":%d},{\"employeeId\":%d,\"status\":\"HALF_DAY\"}]"
                .formatted(a, house, b)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        // the same register again with Nimal left off: Kamal is unchanged, Nimal's mark is removed
        saveDay(MON, "[{\"employeeId\":%d,\"status\":\"PRESENT\",\"projectId\":%d}]".formatted(a, house))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].version").value(0));
        mockMvc.perform(get("/api/v1/attendance").param("from", MON.toString()).param("to", MON.plusDays(6).toString())
                .param("projectId", String.valueOf(house))).andExpect(jsonPath("$[*].employee").value(Matchers.contains("Kamal")));
    }

    @Test
    void attendanceFollowsTheRegisterRules() throws Exception {
        long a = id(create("Kamal", "Labourer", "3000", "560"));
        String one = "[{\"employeeId\":%d,\"status\":\"PRESENT\"}]".formatted(a);
        saveDay(LocalDate.now().plusDays(2), one).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("future")));
        saveDay(MON.minusDays(1), one).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("Sunday")));
        saveDay(LocalDate.of(2025, 12, 1), one).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("joined")));
        saveDay(MON, "[{\"employeeId\":%d,\"status\":\"PRESENT\"},{\"employeeId\":%d,\"status\":\"ABSENT\"}]".formatted(a, a))
                .andExpect(status().isBadRequest());
        saveDay(MON, "[{\"employeeId\":%d,\"status\":\"PRESENT\"}]".formatted(Long.MAX_VALUE)).andExpect(status().isBadRequest());

        long client = jdbc.queryForObject("SELECT client_id FROM projects WHERE id = ?", Long.class, house);
        long design = ApiFixtures.project(mockMvc, client, "DESIGN", "Plan");
        long planning = ApiFixtures.project(mockMvc, client, "CONSTRUCTION", "Not started");
        saveDay(MON, "[{\"employeeId\":%d,\"status\":\"PRESENT\",\"projectId\":%d}]".formatted(a, design))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("construction")));
        saveDay(MON, "[{\"employeeId\":%d,\"status\":\"PRESENT\",\"projectId\":%d}]".formatted(a, planning))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("in progress or on hold")));

        mockMvc.perform(put("/api/v1/employees/" + a + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\",\"version\":0}")).andExpect(status().isOk());
        saveDay(MON, one).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("inactive")));
    }

    @Test
    void overtimeIsRecordedAtTheWorkersRateAndApproved() throws Exception {
        long a = id(create("Kamal", "Labourer", "3000", "560"));
        long ot = id(overtime(a, "2.5", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.hourlyRate").value(560.0)).andExpect(jsonPath("$.amount").value(1400.0))
                .andExpect(jsonPath("$.approved").value(false)));
        overtime(a, "8.5", null).andExpect(status().isBadRequest());
        overtime(a, "0", null).andExpect(status().isBadRequest());
        overtime(a, "1", "700").andExpect(jsonPath("$.hourlyRate").value(700.0));
        mockMvc.perform(put("/api/v1/overtime/" + ot + "/approval").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true,\"version\":0}")).andExpect(jsonPath("$.approved").value(true));
        mockMvc.perform(get("/api/v1/overtime").param("from", MON.toString()).param("to", MON.plusDays(6).toString())
                .param("approved", "true")).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/overtime").param("from", "2026-01-01").param("to", "2026-12-31")).andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/employees").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/attendance").param("from", "2026-09-07").param("to", "2026-09-07")
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    private ResultActions create(String name, String role, String wage, String ot) throws Exception {
        return mockMvc.perform(post("/api/v1/employees").with(user("setup").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","role":"%s","dailyWage":%s,"overtimeRate":%s,"joinedOn":"2026-01-05"}""".formatted(name, role, wage, ot)));
    }

    private ResultActions saveDay(LocalDate date, String entries) throws Exception {
        return mockMvc.perform(put("/api/v1/attendance/" + date).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\":" + entries + "}"));
    }

    private ResultActions overtime(long employeeId, String hours, String rate) throws Exception {
        String rateField = rate == null ? "" : ",\"hourlyRate\":" + rate;
        return mockMvc.perform(post("/api/v1/overtime").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"employeeId":%d,"date":"%s","projectId":%d,"hours":%s,"recordedBy":"Site supervisor"%s}"""
                .formatted(employeeId, MON.plusDays(1), house, hours, rateField)));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
